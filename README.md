# AuthTemplate

## Developing

First off, run `nix develop`.
Your shell should change to something like `[nix-dev:...]$`.
Then, you might need to call `yarn` once to set-up the JavaScript side.

### Backend (BE)

- Source code located in `backend/`
  - shared code between BE and FE is in `shared/`
- Run `sbt backend/run`
  - sadly no auto-rebuilds (at least for now)
  - says the address of the backend

### Frontend (FE)

- Source code located in `frontend/`
    - shared code between BE and FE is in `shared/`
    - CSS is located in `./style.css`
    - HTML is located in `./index.html` (but don't change it, please)
- Run the following commands in separate terminals (auto-rebuilds!):
  - `sbt ~frontend/fastLinkJS` (scala.js to js)
  - `yarn dev` (JS auto-reload using vite)
    - says the address of the frontend

## How does it work?

- We use OAuth to talk to GitHub (which we only need to do on login)
- We use JWT to talk between frontend and backend. There are two kinds of tokens:
    - access tokens: short-lived (15 minutes), used for authentication, passed via a `Authorization` header with `Bearer <ACCESS_TOKEN>`
    - refresh tokens: long-lived (24 hours), used only to get new access tokens, passed via cookies

### Login

1. Frontend `/`, click on "Login via GitHub"
    - redirects the user to `https://github.com/login/oauth/authorize`
    - see `component.Login`
2. GitHub lets the user log in
    - redirects the user to `127.0.0.1:5173/callback?code=<SECRET CODE>` on frontend
3. Frontend `/callback?code=<SECRET CODE>`
    - reads the `<SECRET CODE>` and sends it to the backend via `/api/auth/github`
    - see `component.Callback`
4. Backend `/api/auth/github` gets the secret code in body as JSON
    - sends it to GitHub at `https://github.com/login/oauth/access_token`
    - see `githubToken` in `Server`
    - we have an abstraction over the GitHub API, see `GitHubAPI`
5. GitHub responds with a github access token (takes a while!)
6. Backend `/api/auth/github` gets the response
    - and attempts to get the user details (`tryGetUserDetails` in `GitHubAPI`)
7. GitHub responds with the user details
8. Backend `/api/auth/github` parses the github ID and the github login
    - and then backend creates:
        - an access token sent via JSON in response
        - a refresh token sent via cookies
    - and responds to the frontend
9. Frontend `/callback...` now gets the response
    - parses the access token
    - saves it in local storage
        - see `State.scala` on frontend
10. Congratulations, now the user is logged in! 🎉

### Authentication

#### Frontend

Automatic, provided you use `API.post[T]` and `API.get[T]` :)
They return either:
- a HttpError (if there was a mistake)
- or a value of type `T`

Moreover, if they get a http error `401` (unauthorized),
they try refreshing the token and repeat the request automatically!


#### Backend

Sort-of-automatic, provided you use functions from `API` and `Auth`:

```scala
@withHeaderAuth
@postJsonInput("/myEndpoint")
def serve(age: Int, name: String)(authResult: AuthResult[UserProfile] = handle[MyCoolJson] {
  if (age < 18) {
    throw HttpError(401, "Unauthorized to buy alcohol")
  }

  authResult match {
    case AuthResult.Success(v) => MyCoolJson(???)
    case AuthResult.Failure(reason) => {
        throw HttpError(statusCode = 401, message = "Unauthorized")
    }
    case AuthResult.NoCredentials() => {
        throw HttpError(statusCode = 403, message = "Missing credentials")
    }
  }
}
```

For a real-life example, see `handleRandom()` in `Server`.

You can:
- throw `HttpError`s!
- add the `withHeaderAuth` decoration to get an extra argument which tells you the result of trying to authenticate!
