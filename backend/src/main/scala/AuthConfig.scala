package authtemplate

object AuthConfig {
  // These are loaded from the environment.
  // Use Nix together with `.env` file to get them loaded automagically!
  val OAUTH_CLIENT_SECRET = sys.env("AUTHTEMPLATE_OAUTH_CLIENT_SECRET")
  val JWT_SECRET = sys.env("AUTHTEMPLATE_JWT_SECRET")
}
