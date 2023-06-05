package authtemplate

import com.raquo.airstream.core.EventStream
import com.raquo.airstream.web.{FetchBuilder, FetchOptions}
import com.raquo.laminar.api.L.*
import org.scalajs.dom
import upickle.default.*

import scala.util.{Failure, Success}

/**
 * Contains useful functions for sending requests (with auth header or without)
 * and automatically parsing the response via the upickle library.
 *
 * A simple, thin wrapper over [[FetchStream]].
 */
object API {
  type OptionSetter = FetchOptions[dom.BodyInit] => Unit

  /**
   * Adds an Authorization header to already existing [[OptionSetter]]s.
   */
  private def addAuthHeader(setOptions: OptionSetter*)(using state: State): Seq[OptionSetter] = {
    var optionSetters = setOptions
    state.accessToken.foreach { token =>
      val authHeader = ("Authorization", token.bearer)
      optionSetters = optionSetters.appended(_.headersAppend(authHeader))
    }
    optionSetters
  }

  private def parseResponse[T: Reader](response: org.scalajs.dom.Response): EventStream[Either[HttpError, T]] = {
    EventStream.fromJsPromise(response.text()).map { responseText =>
      println("Parsing:")
      println(responseText)
      if (response.ok) {
        println("Response OK")
        try {
          val value = read[T](responseText)
          Right(value)
        } catch { exception =>
          val errorMessage = s"Encoding error:\n$exception.toString\nwhen parsing:$responseText"
          val error = HttpError(500 /* INTERNAL_SERVER_ERROR */ , errorMessage)
          Left(error)
        }
      } else {
        println("Response not OK")
        val error = HttpError(response.status, responseText)
        Left(error)
      }
    }
  }

  private def refresh(using state: State): EventStream[Boolean] = {
    println("Refresh attempt initiated!")

    FetchStream
      .raw
      .post("/api/auth/refresh")
      .flatMap(parseResponse[APIAuthResponse])
      .map {
        case Left(error) => {
          println(s"Error while refreshing: $error")
          false
        }
        case Right(authResponse) => {
          println(s"Loading auth response after successful refresh: $authResponse")
          state.loadAuthResponse(authResponse)
        }
      }
  }

  /**
   * Send HTTP request containing a JSON [[ujson.Obj]] in body with authorization (if possible)
   * and read the result as JSON of type [[ResultType]].
   * Automatically attempts to refresh token if necessary.
   *
   * @param method     The HTTP method to be used
   * @param url        The target URL (use `/api/...` to reach backend endpoints)
   * @param body       The body of the request to be JSON-encoded
   * @param setOptions option setters allowing to extend the request
   * @tparam ResultType the type of the result, requires that a [[upickle.default.Reader]] is implemented for it
   * @return Returns either the encountered errors or a value of the type T
   */
  private def sendRequestRetry[ResultType: Reader](method: dom.HttpMethod,
                                                   url: String,
                                                   body: ujson.Obj,
                                                   allowRefresh: Boolean = true,
                                                   setOptions: OptionSetter*)
                                                  (using state: State): EventStream[Either[HttpError, ResultType]] = {
    val jsonBody: String = write(body)
    val optionSetters = addAuthHeader(setOptions *).appended(_.body(jsonBody))

    FetchStream
      .raw
      .apply(_ => method, url, setOptions = optionSetters *)
      .flatMap(parseResponse[ResultType])
      .flatMap { // check if we need to refresh
        case Right(value) => EventStream.fromValue(Right(value))
        case Left(error) if error.statusCode == 401 && allowRefresh => {
          println(s"Refresh might be needed, got $error")
          val refreshResponseStream = refresh(using state)
          refreshResponseStream.flatMap { refreshSuccessful =>
            println(s"Refresh result: $refreshSuccessful")
            if (refreshSuccessful) { // success, try to send the request again since the user is logged in now!
              sendRequestRetry[ResultType](method, url, body, allowRefresh = false, setOptions *)
            } else { // failure, report error
              EventStream.fromValue(Left(error))
            }
          }
        }
        case Left(error) => EventStream.fromValue(Left(error))
      }
  }

  /**
   * Send GET request containing a JSON [[ujson.Obj]] in body with authorization (if possible)
   * and read the result as JSON of type [[ResultType]].
   * Automatically attempts to refresh token if necessary.
   *
   * @param url        The target URL (use `/api/...` to reach backend endpoints)
   * @param body       The body of the request to be JSON-encoded
   * @param setOptions option setters allowing to extend the request
   * @tparam ResultType the type of the result, requires that a [[upickle.default.Reader]] is implemented for it
   * @return Returns either the encountered errors or a value of the type T
   */
  def get[ResultType: Reader](url: String, body: ujson.Obj, setOptions: OptionSetter*)(using state: State): EventStream[Either[HttpError, ResultType]] = {
    sendRequestRetry[ResultType](dom.HttpMethod.GET, url, body, allowRefresh = true, setOptions *)
  }

  /**
   * Send POST request containing a JSON [[ujson.Obj]] in body with authorization
   * and read the result as JSON of type [[ResultType]].
   * Automatically attempts to refresh token if necessary.
   *
   * @param url        The target URL (use `/api/...` to reach backend endpoints)
   * @param body       The body of the request to be JSON-encoded
   * @param setOptions option setters allowing to extend the request
   * @tparam ResultType the type of the result, requires that a [[upickle.default.Reader]] is implemented for it
   * @return Returns either the encountered errors or a value of the type T
   */
  def post[ResultType: Reader](url: String, body: ujson.Obj, setOptions: OptionSetter*)(using state: State): EventStream[Either[HttpError, ResultType]] = {
    sendRequestRetry[ResultType](dom.HttpMethod.POST, url, body, allowRefresh = true, setOptions *)
  }
}
