package authtemplate

import cask.Cookie
import ujson.Value.Value
import upickle.default.*


object Server extends ServerBase {
  private val randGen = scala.util.Random

  @withCookieAuth
  @cask.post("/auth/refresh")
  def refresh()(authResult: AuthResult[UserProfile]) = handleWithCookies[APIAuthResponse] {
    authResult match {
      case AuthResult.Success(userProfile) => {
        val accessToken = Auth.makeAccessToken(userProfile)
        val refreshTokenCookies = Auth.makeRefreshTokenCookies(userProfile)

        (APIAuthResponse(accessToken), refreshTokenCookies)
      }
      case AuthResult.Failure(reason: String) => {
        println(s"Auth failure in '/auth/refresh' because: $reason")
        throw HttpError(statusCode = 401, message = "Unauthorized")
      }
      case AuthResult.NoCredentials() => {
        println("Auth no credentials in '/auth/refresh'")
        throw HttpError(statusCode = 403, message = "Missing credentials")
      }
    }
  }

  @withHeaderAuth
  @cask.post("/auth/logout")
  def logout()(authResult: AuthResult[UserProfile]) = handleWithCookies[String] {
    authResult match {
      case AuthResult.Success(_userProfile) =>
        ("", Auth.makeUnsetRefreshTokenCookies)
      case AuthResult.Failure(reason: String) => {
        println(s"Auth failure in '/auth/logout' because: $reason")
        throw HttpError(statusCode = 401, message = "Unauthorized")
      }
      case AuthResult.NoCredentials() => {
        println("Auth no credentials in '/auth/logout'")
        throw HttpError(statusCode = 403, message = "Missing credentials")
      }
    }
  }

  @postJsonInput("/auth/github")
  def githubToken(code: String) = handleWithCookies[APIAuthResponse] {
    println(s"Answering '/auth/github?code=$code'")

    try {
      val githubAccessToken = GitHubAPI.tryGetAccessToken(code)
      val githubUserDetails = GitHubAPI.tryGetUserDetails(githubAccessToken)
      val userProfile = UserProfile(githubUserDetails.id.toString, githubUserDetails.login)

      val accessToken = Auth.makeAccessToken(userProfile)
      val refreshTokenCookies = Auth.makeRefreshTokenCookies(userProfile)
      (APIAuthResponse(accessToken), refreshTokenCookies)
    } catch { e =>
      println(s"Caught error in '/auth/github': ${e.getMessage}")
      throw HttpError(statusCode = 401, message = "Unauthorized")
    }
  }

  @withHeaderAuth
  @postJsonInput("/random")
  def handleRandom()(authResult: AuthResult[UserProfile]) = handle[APIRandomNumber] {
    println("Answering '/random'")
    authResult match {
      case AuthResult.Success(v) => {
        println(s"Auth success in '/random': $v")
        APIRandomNumber(n = randGen.between(1, 10))
      }
      case AuthResult.Failure(reason) => {
        println(s"Auth failure in '/random' because: $reason")
        throw HttpError(statusCode = 401, message = "Unauthorized")
      }
      case AuthResult.NoCredentials() => {
        println("Auth no credentials in '/random'")
        throw HttpError(statusCode = 403, message = "Missing credentials")
      }
    }
  }


  initialize()
}