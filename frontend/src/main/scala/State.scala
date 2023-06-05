package authtemplate

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import upickle.default.read


class State private(_accessToken: Var[Option[JWTToken]], _userProfile: Var[Option[UserProfile]]) {
  def clear(): Unit = {
    println("Clearing state!")
    _accessToken.set(None)
    _userProfile.set(None)
    State.deleteToken()
  }

  val accessTokenSignal: Signal[Option[JWTToken]] = _accessToken.signal

  def accessToken: Option[JWTToken] = _accessToken.now()

  def setAccessToken(token: String): Unit = {
    val jwtToken = JWTToken(token)
    _accessToken.set(Some(jwtToken))
    State.setToken(jwtToken)
  }

  val userProfileSignal: Signal[Option[UserProfile]] = _userProfile.signal

  def userProfile: Option[UserProfile] = _userProfile.now()

  def setUserProfile(userProfile: UserProfile): Unit = {
    println("Set user profile!")
    _userProfile.set(Some(userProfile))
  }

  def loggedIn: Boolean = _accessToken.now().isDefined

  // This function might arguably belong somewhere else into a separate Auth module?
  def loadAuthResponse(authResponse: APIAuthResponse): Boolean = {
    try {
      val APIAuthResponse(jwt) = authResponse
      val profile = State.tryParseJwtPayload(jwt)

      this.setAccessToken(jwt)
      this.setUserProfile(profile)
      true
    } catch { e =>
      println(s"Error encountered while loading auth response: $e")
      this.clear()
      false
    }
  }
}

object State {
  def initial: State = {
    println("Creating a new state!")
    val accessToken = getToken
    val userProfile = accessToken.flatMap { jwt =>
      try {
        val profile = tryParseJwtPayload(jwt.value)
        Some(profile)
      } catch { e =>
        None
      }
    }
    State(Var(accessToken), Var(userProfile))
  }

  private val ACCESS_TOKEN_KEY = "authtemplate-access-token"

  // Security note: Using 'localStorage' is suboptimal
  // (see [[https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html#token-storage-on-client-side]])
  // ideally we would use 'sessionStorage' instead.

  // However, that makes everything much more difficult
  // (specifically the user can't have the app open in multiple tabs anymore!)
  private def getToken: Option[JWTToken] = {
    Option(dom.window.localStorage.getItem(ACCESS_TOKEN_KEY)).map { value =>
      JWTToken(value)
    }
  }

  private def setToken(token: JWTToken): Unit =
    dom.window.localStorage.setItem(ACCESS_TOKEN_KEY, token.value)

  private def deleteToken(): Unit = {
    dom.window.localStorage.removeItem(ACCESS_TOKEN_KEY)
  }

  private def tryParseJwtPayload(jwt: String): UserProfile = {
    // TODO: Use JwtUpickle instead to parse the user profile out of the jwt!
    // val jwtClaim = JwtUpickle.decode(token = jwt, options = JwtOptions(leeway = 30)).get

    val jwtPayload = jwt.split('.')(1)
    val jwtClaim = String(java.util.Base64.getUrlDecoder.decode(jwtPayload.getBytes))
    read[UserProfile](jwtClaim)
  }
}
