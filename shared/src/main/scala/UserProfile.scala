package authtemplate

import upickle.default.*

/**
 * Details we're remembering about the user.
 * Feel free to extend with user roles and similar information.
 */
case class UserProfile(githubUserId: String, githubLogin: String) derives ReadWriter
