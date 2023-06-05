package authtemplate

import upickle.default.*

object GitHubAPI {
  // subset of what GitHub returns from `https://api.github.com/user`
  // we're only interested in the login and the github user id
  case class UserDetails(login: String, id: Int) derives ReadWriter

  case class Error(error: String, error_description: String, error_uri: String) extends Throwable derives ReadWriter {
    override def getMessage(): String = s"GitHub Error: [$error] $error_description"
  }

  def tryGetAccessToken(code: String): String = {
    println(s"Getting access token from code: $code")

    val data = Map("client_id" -> Config.OAUTH_CLIENT_ID, "client_secret" -> AuthConfig.OAUTH_CLIENT_SECRET, "code" -> code)
    val headers = Map("Accept" -> "application/json") // so that the response is a JSON

    val result = requests.post("https://github.com/login/oauth/access_token", data = data, headers = headers)
    val json = ujson.read(result.text())

    readJsonKey(json, "access_token") match {
      case Some(value) => value
      case None => {
        // if there is no 'access_token' inside, it might be an error, let's try to parse it
        val error = read[Error](json)
        throw error
      }
    }
  }

  def tryGetUserDetails(accessToken: String): UserDetails = {
    println(s"Getting user details from GitHub with token: $accessToken")

    val headers = Map("Accept" -> "application/vnd.github+json", "Authorization" -> s"Bearer ${accessToken}")
    val result = requests.get("https://api.github.com/user", headers = headers)

    read[UserDetails](result.text())
  }

  private def readJsonKey(json: ujson.Value, keyName: String): Option[String] = {
    for {
      obj <- json.objOpt
      key <- obj.get(keyName)
      keyString <- key.strOpt
    } yield keyString
  }
}