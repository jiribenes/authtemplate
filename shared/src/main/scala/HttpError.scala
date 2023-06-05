package authtemplate

/**
 * Represents a HTTP error.
 *
 * @param statusCode HTTP status code
 * @param message Description of the problem
 */
class HttpError(val statusCode: Int, val message: String) extends Exception {
  override def getMessage: String = s"HTTP $statusCode: $message"
}