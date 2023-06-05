package authtemplate
package component

import com.raquo.laminar.api.L.*
import com.raquo.laminar.modifiers.Binder.Base
import com.raquo.waypoint.*
import org.scalajs.dom
import upickle.default.*

object Content {
  private val randomVar: Var[Option[Int]] = Var(None)

  private def aggregateChanges[T](signal: Signal[Option[T]]): Signal[List[T]] = {
    signal.changes.scanLeft(List[T]()) { (list, optInt) =>
      optInt match
        case Some(n) => n :: list
        case None => list
    }
  }

  def apply(using router: Router[Page], state: State): HtmlElement = {
    val username = state.userProfileSignal.map { optUserProfile =>
      optUserProfile.map(_.githubLogin).getOrElse("<error>")
    }

    div(
      authenticatedOnly,
      p(
        "Secret content for authenticated users only!",
        br(),
        child.text <-- username
      ),
      hr(),
      button(
        "Click me to get a random number!",
        onClick.preventDefault.flatMap { _ =>
          val body = ujson.Obj() // empty JSON object
          API.post[APIRandomNumber]("/api/random", body).map {
            case Left(err) => {
              println(s"Encountered an error in '/api/random': $err")
              None
            }
            case Right(apiResponse) => Some(apiResponse.n)
          }
        } --> randomVar
      ),
      div(
        "Your random number is: ",
        child.maybe <-- randomVar.signal.map(_.map(_.toString))
      ),
      div(
        "Your previous random numbers were: ",
        child.text <-- aggregateChanges(randomVar.signal).map(_.toString)
      )
    )
  }

  private def authenticatedOnly(using router: Router[Page], state: State) =
    state.accessTokenSignal --> { tok =>
      if tok.isEmpty then Page.redirectTo(Page.Login())
    }
}
