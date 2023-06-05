import org.scalajs.linker.interface.ModuleSplitStyle

name := "template-scala"

version := "0.1.0"

// Global / onChangedBuildSource := ReloadOnSourceChanges

val sharedSettings = Seq(
  scalaVersion := "3.2.2",
  doc / sources := Nil,
  packageDoc / publishArtifact := false,
  libraryDependencies ++= Seq()
)

// avoid generating docs
// Compile / doc / sources                := Nil
// Compile / packageDoc / publishArtifact := false

lazy val shared = project
  .in(file("shared"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    sharedSettings,
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
    },
    scalaJSLinkerConfig ~= {
      // TODO: only for our path
      _.withModuleSplitStyle(ModuleSplitStyle.FewestModules)
    },
    scalaJSLinkerConfig ~= {
      _.withSourceMap(false)
    },
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "upickle" % "3.1.0",
      "com.github.jwt-scala" %% "jwt-upickle" % "9.3.0"
    )
  )

lazy val backend = project
  .in(file("backend"))
  .enablePlugins(JavaAppPackaging)
  .settings(
    sharedSettings,
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "cask" % "0.9.0",
      "com.lihaoyi" %% "requests" % "0.8.0"
    )
  )
  .dependsOn(shared)

lazy val frontend = project
  .in(file("frontend"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    sharedSettings,
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
    },
    scalaJSLinkerConfig ~= {
      // TODO: only for our path
      _.withModuleSplitStyle(ModuleSplitStyle.FewestModules)
    },
    scalaJSLinkerConfig ~= {
      _.withSourceMap(false)
    },
    scalaJSUseMainModuleInitializer := true,

    libraryDependencies ++= Seq(
      "com.raquo" %%% "laminar" % "15.0.1",
      "com.raquo" %%% "waypoint" % "6.0.0",
      "com.lihaoyi" %%% "upickle" % "3.1.0",
      "io.laminext" %%% "fetch-upickle" % "0.15.0",
      "org.scala-js" %%% "scala-js-macrotask-executor" % "1.1.1"
    )
  ).dependsOn(shared)
