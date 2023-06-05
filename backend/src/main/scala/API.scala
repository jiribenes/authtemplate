package authtemplate

import cask.endpoints.{JsReader, JsonData}
import cask.internal.Util
import cask.model.Response.{DataCompanion, Raw}
import cask.model.{Request, Response, Cookie}
import cask.router.{HttpEndpoint, Result}

import java.io.{ByteArrayOutputStream, OutputStream}
import upickle.default.*

/**
 * Helper function used for catching [[HttpError]] exceptions
 * and for serializing the output to a [[cask.Response]].
 *
 * @see [[postJsonInput]] for more details on how to use this function.
 */
def handle[T: Writer](data: => T): cask.Response[String] = {
  try {
    cask.Response(statusCode = 200, data = write[T](data))
  } catch {
    case e: HttpError =>
      cask.Response(statusCode = e.statusCode, data = e.message)
  }
}

/**
 * Helper function used for catching [[HttpError]] exceptions
 * and for serializing the output to a [[cask.Response]].
 * In addition, it allows the user to return cookies!
 */
def handleWithCookies[T: Writer](data: => (T, Seq[Cookie])): cask.Response[String] = {
  try {
    val (result, cookies) = data
    cask.Response(statusCode = 200, data = write[T](result), cookies = cookies)
  } catch {
    case e: HttpError =>
      cask.Response(statusCode = e.statusCode, data = e.message)
  }
}

/**
 * Custom endpoint combinator.
 * Is not responsible for returning things JSON-encoded, please use [[handle]] for that purpose.
 *
 * @example
 * {{{
 * @postJsonInput("/myEndpoint")
 * def serve(age: Int, name: String) = handle[MyCoolJson] {
 *   if (age < 18) {
 *     throw HttpError(401, "Unauthorized to buy alcohol")
 *   }
 *
 *   MyCoolJson(???)
 * }
 * }}}
 */
class postJsonInput(val path: String, override val subpath: Boolean = false)
  extends HttpEndpoint[Response[cask.Response.Data], ujson.Value] {
  val methods = Seq("post")
  type InputParser[T] = JsReader[T]

  def wrapFunction(ctx: Request,
                   delegate: Delegate): Result[Response.Raw] = {
    val obj = for {
      str <-
        try {
          val boas = new ByteArrayOutputStream()
          Util.transferTo(ctx.exchange.getInputStream, boas)
          Right(new String(boas.toByteArray))
        }
        catch {
          case e: Throwable => Left(cask.model.Response(
            "Unable to deserialize input JSON text: " + e + "\n" + Util.stackTraceString(e),
            statusCode = 400
          ))
        }
      json <-
        try Right(ujson.read(str))
        catch {
          case e: Throwable => Left(cask.model.Response(
            "Input text is invalid JSON: " + e + "\n" + Util.stackTraceString(e),
            statusCode = 400
          ))
        }
      obj <-
        try Right(json.obj)
        catch {
          case e: Throwable => Left(cask.model.Response(
            "Input JSON must be a dictionary",
            statusCode = 400
          ))
        }
    } yield obj.toMap
    obj match {
      case Left(r) => Result.Success(r.map(Response.Data.WritableData(_)))
      case Right(params) => delegate(params)
    }
  }

  def wrapPathSegment(s: String): ujson.Value = ujson.Str(s)
}