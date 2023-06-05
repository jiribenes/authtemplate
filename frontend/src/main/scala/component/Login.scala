package authtemplate
package component

import com.raquo.laminar.api.L.*

object Login {
  val OAUTH_LINK = s"https://github.com/login/oauth/authorize?redirect_uri=${Config.OAUTH_REDIRECT_URI}&scope=${Config.OAUTH_SCOPE}&client_id=${Config.OAUTH_CLIENT_ID}"

  val body: Div = {
    div(
      p("Hello, there!"),
      a(
        href := OAUTH_LINK,
        "Login via GitHub"
      )
    )
  }

  def apply() = body
}
