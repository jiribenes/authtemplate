package authtemplate

import pdi.jwt.JwtClaim
import ujson.Value


/**
 * Server base definition.
 */
class ServerBase extends cask.MainRoutes {
  override def main(args: Array[String]): Unit = {
    println(s"Starting server.\nBackend should be available on: http://${host}:${port}")

    if (!verbose) cask.main.Main.silenceJboss()
    val server = io.undertow.Undertow.builder
      .addHttpListener(port, host)
      .setHandler(defaultHandler)
      .build

    server.start()

    def shutDown() = {
      println("Shutting down backend.")
      server.stop()
    }

    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run() = shutDown()
    })

    // Wait for any input and then shut down server.
    println("Backend is running, press <Enter> to shut it down.")
    Console.in.readLine()
    shutDown()
  }
}