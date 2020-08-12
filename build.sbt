val V = new {
  val Scala = "2.13.3"
  val ScalaGroup = "2.13"

  val cats = "2.1.1"
  val laminar = "0.9.2"
  val http4s = "0.21.6"
  val sttp = "2.2.4"
  val circe = "0.13.0"
  val decline = "1.2.0"
  val organiseImports = "0.4.0"
  val betterMonadicFor = "0.3.1"
  val utest = "0.7.4"
}

val Dependencies = new {
  private val http4sModules =
    Seq("dsl", "blaze-client", "blaze-server", "circe").map("http4s-" + _)

  private val sttpModules = Seq("core", "circe")

  lazy val frontend = Seq(
    libraryDependencies ++=
      sttpModules.map("com.softwaremill.sttp.client" %%% _ % V.sttp) ++
        Seq("com.raquo" %%% "laminar" % V.laminar) ++
        Seq("com.lihaoyi" %%% "utest" % V.utest % Test)
  )

  lazy val backend = Seq(
    libraryDependencies ++=
      http4sModules.map("org.http4s" %% _ % V.http4s) ++
        Seq("com.monovore" %% "decline" % V.decline)
  )

  lazy val shared = new {
    val js = libraryDependencies += "io.circe" %%% "circe-generic" % V.circe
    val jvm = libraryDependencies += "io.circe" %% "circe-generic" % V.circe
  }
}

inThisBuild(
  Seq(
    scalafixDependencies += "com.github.liancheng" %% "organize-imports" % V.organiseImports,
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalafixScalaBinaryVersion := V.ScalaGroup
  )
)

lazy val root =
  (project in file(".")).aggregate(frontend, backend, shared.js, shared.jvm)

import sbt._

lazy val frontend = (project in file("modules/frontend"))
  .dependsOn(shared.js)
  .enablePlugins(ScalaJSPlugin)
  .enablePlugins(ScalablyTypedConverterPlugin)
  .settings(scalaJSUseMainModuleInitializer := true)
  .settings(
    Dependencies.frontend,
    Dependencies.shared.js,
    testFrameworks += new TestFramework("utest.runner.Framework"),
    jsEnv in Test := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv()
  )
  .settings(commonBuildSettings)
  .settings(
    Compile / npmDependencies ++= Seq(
      "monaco-editor" -> "0.20.0"
    )
  )
  .settings(
    webpackCliVersion := "3.3.10",
    npmDevDependencies in Compile ++= Seq(
      "webpack-merge" -> "5.1.1",
      "style-loader" -> "1.2.1",
      "css-loader" -> "4.2.1",
      "file-loader" -> "1.1.11",
      "sass-loader" -> "9.0.3"
    )
  )
  .settings(
    // webpackBundlingMode := BundlingMode.LibraryAndApplication(),
    webpackConfigFile := Some(baseDirectory.value / "webpack.config.js")
  )

lazy val backend = (project in file("modules/backend"))
  .dependsOn(shared.jvm)
  .settings(Dependencies.backend)
  .settings(commonBuildSettings)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .settings(
    mappings in Universal += {
      val appJs = (frontend / Compile / fullOptJS).value.data
      appJs -> ("lib/prod.js")
    },
    javaOptions in Universal ++= Seq(
      "--port 8080",
      "--mode prod"
    ),
    packageName in Docker := "laminar-http4s-example"
  )

lazy val shared = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/shared"))
  .jvmSettings(Dependencies.shared.jvm)
  .jsSettings(Dependencies.shared.js)
  .jsSettings(commonBuildSettings)
  .jvmSettings(commonBuildSettings)

lazy val fastOptCompileCopy = taskKey[Unit]("")

val jsPath = "modules/backend/src/main/resources/assets"

fastOptCompileCopy := {
  val sources = (frontend / Compile / fastOptJS / webpack).value
  sources.foreach {f => 
    IO.copyFile(
      f.data,
      baseDirectory.value / jsPath / f.data.name
    )
  }
}

lazy val fullOptCompileCopy = taskKey[Unit]("")

fullOptCompileCopy := {
  val sources = (frontend / Compile / fullOptJS / webpack).value
  sources.foreach {f => 
    IO.copyFile(
      f.data,
      baseDirectory.value / jsPath / f.data.name
    )
  }

}

lazy val commonBuildSettings: Seq[Def.Setting[_]] = Seq(
  scalaVersion := V.Scala,
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % V.betterMonadicFor),
  scalacOptions ++= Seq(
    "-Ywarn-unused"
  )
)

addCommandAlias("runDev", ";fastOptCompileCopy; backend/reStart --mode dev")
addCommandAlias("runProd", ";fullOptCompileCopy; backend/reStart --mode prod")

val scalafixRules = Seq(
  "OrganizeImports",
  "DisableSyntax",
  "LeakingImplicitClassVal",
  "ProcedureSyntax",
  "NoValInForComprehension"
).mkString(" ")

val CICommands = Seq(
  "clean",
  "backend/compile",
  "backend/test",
  "frontend/compile",
  "frontend/fastOptJS",
  "frontend/test",
  "scalafmtCheckAll",
  s"scalafix --check $scalafixRules"
).mkString(";")

val PrepareCICommands = Seq(
  s"compile:scalafix --rules $scalafixRules",
  s"test:scalafix --rules $scalafixRules",
  "test:scalafmtAll",
  "compile:scalafmtAll",
  "scalafmtSbt"
).mkString(";")

addCommandAlias("ci", CICommands)

addCommandAlias("preCI", PrepareCICommands)
