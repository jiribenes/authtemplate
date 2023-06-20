package authtemplate
package component

import com.raquo.laminar.api.L.*
import upickle.default.*

object Callback {
  private def githubTokenAuth(code: String)(using state: State): EventStream[Option[APIAuthResponse]] = {
    val body = ujson.Obj("code" -> code)

    API.post[APIAuthResponse]("/api/auth/github", body).map {
      case Left(err) => {
        println(s"Error from '/api/auth/github': $err")
        None
      }
      case Right(apiResponse) => Some(apiResponse)
    }
  }

  def apply(callbackSignal: Signal[Page.Callback])(using state: State) = {
    val response: EventStream[Boolean] =
      callbackSignal.flatMap { user =>
        githubTokenAuth(user.code).flatMap {
          case None => EventStream.empty
          case Some(apiAuthResponse) => state.loadAuthResponse(apiAuthResponse)
        }
      }

    val username = response.map { _ =>
      state.userProfile.map(_.githubLogin).getOrElse("<error>")
    }.toSignal("... loading ...")

    div(
      "Your GitHub username is: ",
      i(
        child.text <-- username
      ),
      child.maybe <-- state.userProfileSignal.map {
        _.map { _ =>
          div(
            "You are logged in! ",
            a(Page.navigateTo(Page.Content()), "Go to '/content'")
          )
        }
      }
    )
  }
}
