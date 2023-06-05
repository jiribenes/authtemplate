package authtemplate

import cask.model.Request
import authtemplate.GitHubAPI.UserDetails
import pdi.jwt.algorithms.JwtHmacAlgorithm
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtOptions, JwtUpickle}
import upickle.default.*

import java.time.Instant
import scala.util.{Failure, Success}

/**
 * Result of an authentication attempt, parametrized by `V`
 */
enum AuthResult[V] {
  /**
   * The authentication was successful and produced a result of type `V`.
   */
  case Success(v: V)

  /**
   * The authentication was unsuccessful because the credentials were incorrect.
   */
  case Failure(reason: String)

  /**
   * The authentication was unsuccessful because no credentials were provided.
   */
  case NoCredentials()
}

/**
 * Custom decorator which automatically attempts to authenticate the user.
 *
 * @example
 * {{{
 * @withHeaderAuth
 * @cask.post("/api")
 * def api(param: String)(authResult: AuthResult[UserProfile]): String = {
 *   ???
 * }
 * }}}
 */
class withHeaderAuth extends cask.RawDecorator {
  override def wrapFunction(request: cask.Request, delegate: Delegate): cask.router.Result[cask.Response.Raw] = {
    println(s"With header auth combinator: called with request: $request")
    val authResult: AuthResult[UserProfile] = Auth.authorizeHeader(request)
    println(s"Auth header result: $authResult")
    delegate(Map("authResult" -> authResult))
  }
}

/**
 * Used only for refreshing via refresh token!
 *
 * @see [[withHeaderAuth]] for the combinator you probably want.
 */
class withCookieAuth extends cask.RawDecorator {
  override def wrapFunction(request: cask.Request, delegate: Delegate): cask.router.Result[cask.Response.Raw] = {
    println(s"With header auth combinator: called with request: $request")
    val authResult: AuthResult[UserProfile] = Auth.authorizeCookies(request)
    println(s"Auth cookie result: $authResult")
    delegate(Map("authResult" -> authResult))
  }
}

object Auth {
  private val SECRET_KEY = AuthConfig.JWT_SECRET
  private val ALGORITHM: JwtHmacAlgorithm = JwtAlgorithm.HS512
  private val REFRESH_TOKEN_EXPIRATION: Long = 60 * 60 * 24 // 24 hours
  private val ACCESS_TOKEN_EXPIRATION: Long = 60 * 15 // 15 minutes
  private val REFRESH_TOKEN_COOKIE_NAME: String = "authtemplate-refresh-token"

  def makeAccessToken(userProfile: UserProfile): String =
    encode(userProfile, ACCESS_TOKEN_EXPIRATION)

  def makeRefreshTokenCookies(userProfile: UserProfile): Seq[cask.Cookie] = {
    val refreshToken = makeRefreshToken(userProfile)
    val cookie = cask.Cookie(name = REFRESH_TOKEN_COOKIE_NAME, value = refreshToken, expires = Instant.now.plusSeconds(REFRESH_TOKEN_EXPIRATION))
    Seq(cookie)
  }

  def makeUnsetRefreshTokenCookies: Seq[cask.Cookie] = {
    val cookie = cask.Cookie(name = REFRESH_TOKEN_COOKIE_NAME, value = "", expires = Instant.EPOCH.plusSeconds(1000), maxAge = 0)
    Seq(cookie)
  }

  private def makeRefreshToken(userProfile: UserProfile): String =
    encode(userProfile, REFRESH_TOKEN_EXPIRATION)

  /**
   * Encodes user data into a JWT token serialized into a string
   */
  private def encode(userProfile: UserProfile, expiration: Long): String = {
    println(s"Encoding $userProfile to a JWT token")
    val claim = JwtClaim(
      content = write[UserProfile](userProfile),
      expiration = Some(Instant.now.plusSeconds(expiration).getEpochSecond),
      issuedAt = Some(Instant.now.getEpochSecond),
      issuer = Some("authtemplate:token"),
      subject = Some(userProfile.githubUserId)
    )

    val token = JwtUpickle.encode(claim = claim, key = SECRET_KEY, algorithm = ALGORITHM)
    token
  }

  /**
   * Decodes a UserProfile from a JWT token encoded in a string.
   * Can throw an exception when the token cannot be decoded.
   */
  private def tryDecode(token: String): JwtClaim = {
    println(s"Decoding $token from a string to a JWT claim")
    val decoded: util.Try[JwtClaim] =
      JwtUpickle.decode(token = token, key = SECRET_KEY, algorithms = Seq(ALGORITHM), options = JwtOptions(leeway = 30))

    decoded.get
  }

  /**
   * Attempts to authorize a Request based on an Authorization header.
   */
  def authorizeHeader(request: cask.Request): AuthResult[UserProfile] = {
    println(s"Trying to authorize ${request.headers}")
    getCaseInsensitive(request.headers, "Authorization").map(_.mkString) match
      case None => AuthResult.NoCredentials() // no auth header => no credentials
      case Some(header) => {
        val bearer = "Bearer "
        val (mbearer, tokenString) = header.splitAt(bearer.length)

        if (mbearer != bearer) {
          return AuthResult.NoCredentials()
        }

        try {
          val jwtClaim = tryDecode(tokenString)
          println(s"Authorizing: decoded header jwt claim: $jwtClaim")
          val userProfile = read[UserProfile](jwtClaim.content)
          AuthResult.Success(userProfile)
        } catch { e =>
          AuthResult.Failure(e.toString)
        }
      }
  }

  /**
   * Attempts to authorize a Request based on a refresh token in cookies.
   */
  def authorizeCookies(request: cask.Request): AuthResult[UserProfile] = {
    println(s"Trying to authorize ${request.headers}")
    getCaseInsensitive(request.cookies, REFRESH_TOKEN_COOKIE_NAME) match
      case None => AuthResult.NoCredentials() // no auth header => no credentials
      case Some(cookie) => {
        try {
          val jwtClaim = tryDecode(cookie.value)
          println(s"Authorizing: decoded cookies jwt claim: $jwtClaim")
          val userProfile = read[UserProfile](jwtClaim.content)
          AuthResult.Success(userProfile)
        } catch { e =>
          AuthResult.Failure(e.toString)
        }
      }
  }
}
