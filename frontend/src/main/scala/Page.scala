package authtemplate

import com.raquo.laminar.api.L.*
import com.raquo.waypoint.*
import org.scalajs.dom
import upickle.default.*

sealed trait Page derives ReadWriter {
  def name: String
}

object Page {
  case class Callback(code: String) extends Page {
    override def name: String = "AuthTemplate - Callback from GitHub"
  }

  case class Login() extends Page {
    override def name: String = "AuthTemplate - Login"
  }

  case class Content() extends Page {
    override def name: String = "AuthTemplate - Content"
  }

  val callbackRoute: Route[Callback, String] = Route.onlyQuery(
    encode = callbackPage => callbackPage.code,
    decode = arg => Callback(code = arg),
    pattern = (root / "callback" / endOfSegments) ? (param[String]("code"))
  )
  val loginRoute: Route[Page, Unit] = Route.static(Login(), root / endOfSegments)
  val contentRoute: Route[Page, Unit] = Route.static(Content(), root / "content" / endOfSegments)

  val router = new Router[Page](
    routes = List(callbackRoute, loginRoute, contentRoute),
    getPageTitle = _.name,
    serializePage = page => write[Page](page),
    deserializePage = pageStr => read[Page](pageStr)
  )(
    popStateEvents = windowEvents(
      _.onPopState
    ),
    owner = unsafeWindowOwner // this router will live as long as the window
  )

  /**
   * Use this function to redirect to a different [[Page]], possibly losing the [[State]] in the process.
   *
   * If at all possible, please use [[navigateTo]] instead!
   */
  def redirectTo(page: Page) =
    router.pushState(page)

  /**
   * Use for navigating between pages safely without breaking [[State]].
   *
   * Taken from Waypoint documentation [[https://github.com/raquo/Waypoint#responding-to-link-clicks]]
   */
  def navigateTo(page: Page): Binder[HtmlElement] = Binder { el =>
    val isLinkElement = el.ref.isInstanceOf[dom.html.Anchor]

    if (isLinkElement) {
      el.amend(href(router.absoluteUrlForPage(page)))
    }

    // If element is a link and user is holding a modifier while clicking:
    //  - Do nothing, browser will open the URL in new tab / window / etc. depending on the modifier key
    // Otherwise:
    //  - Perform regular pushState transition
    (onClick
      .filter(ev => !(isLinkElement && (ev.ctrlKey || ev.metaKey || ev.shiftKey || ev.altKey)))
      .preventDefault --> { _ => router.pushState(page) }
      ).bind(el)
  }
}