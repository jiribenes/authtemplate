package authtemplate

import com.raquo.laminar.api.L.*
import com.raquo.waypoint.*
import authtemplate.component.{Content, Login}
import org.scalajs.dom
import pdi.jwt.{JwtOptions, JwtUpickle}

object App {
  def renderPage(using router: Router[Page], state: State): Signal[HtmlElement] = {
    SplitRender[Page, HtmlElement](router.currentPageSignal)
      .collectSignal[Page.Callback] { callbackPageSignal =>
        component.Callback(callbackPageSignal)
      }
      .collectStatic(Page.Login()) {
        component.Login()
      }
      .collectStatic(Page.Content()) {
        component.Content(using router) // no idea why, but passing the router explicitly is necessary here
      }
      .signal
  }

  def app(using state: State) = {
    given router: Router[Page] = Page.router

    val logoutButton = button(
      "Logout",
      // instead of directly logging out, you could first transfer to a `/logout` page on frontend properly,
      // and only then clear state and redirect (which would be a bit cleaner)
      onClick.preventDefault.flatMap { _ =>
        state.clear()

        Page.redirectTo(Page.Login())

        // send logout to server to clear cookies
        API.post[String]("/api/auth/logout", ujson.Obj())
      } --> { _ => () } // <- otherwise the action doesn't get performed...
    )

    div(
      h1("Auth Template"),
      // this is not very nice :/
      child.maybe <-- state.accessTokenSignal.map(_.map(_ => logoutButton)),
      // render the actual page
      child <-- renderPage(using router, state),
    )
  }


  def main(args: Array[String]): Unit = {
    // initialize implicit values
    given state: State = State.initial

    // This div, its id and contents are defined in index.html
    lazy val container = dom.document.getElementById("app")

    lazy val appElement = app(using state)

    // Wait until the DOM is loaded, otherwise app-container element might not exist
    renderOnDomContentLoaded(container, appElement)
  }
}
