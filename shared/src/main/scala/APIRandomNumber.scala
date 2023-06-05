package authtemplate

import upickle.default._

case class APIRandomNumber(n: Int) derives ReadWriter
