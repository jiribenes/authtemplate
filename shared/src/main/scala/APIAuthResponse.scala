package authtemplate

import upickle.default.ReadWriter

case class APIAuthResponse(accessToken: String) derives ReadWriter