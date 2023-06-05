package authtemplate

import upickle.default.*

case class JWTToken(value: String) derives ReadWriter {
  val bearer: String = s"Bearer ${value}"
}
