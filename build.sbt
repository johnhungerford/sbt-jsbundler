import scala.util.Try

inThisBuild(List(
  scalaVersion := "2.12.20",
  sbtPlugin := true,
  organization := "io.github.johnhungerford",
  organizationName := "johnhungerford",
  organizationHomepage := Some(url("https://johnhungerford.github.io")),
  homepage := Some(url("https://johnhungerford.github.io")),
  licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  startYear := Some(2024),
  developers := List(
    Developer(
      id    = "johnhungerford",
      name  = "John Hungerford",
      email = "jiveshungerford@gmail.com",
      url   = url( "https://johnhungerford.github.io" )
    )
  ),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/johnhungerford/sbt-jsbundler"),
      "scm:git@github.com:johnhungerford/sbt-jsbundler.git"
    )
  ),
  sonatypeCredentialHost := "s01.oss.sonatype.org",
  sonatypeRepository := "https://s01.oss.sonatype.org/service/local",
))

console / initialCommands := """import sbtjsbundler._"""

enablePlugins(ScriptedPlugin)
// set up 'scripted; sbt plugin for testing sbt plugins
scriptedLaunchOpts ++=
  Seq("-Xmx1024M")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.15.0")

val setPlugins = taskKey[Unit]("Inject plugins into scripted test projects with correct version")

setPlugins := {
  IO.listFiles(file("src/sbt-test/sbt-jsbundler")).toList.foreach(file => {
    if (file.isDirectory) {
      val pluginsSbt =
        s"""addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.15.0")
           |addSbtPlugin("io.github.johnhungerford" % "sbt-jsbundler" % "${version.value}")
		   |libraryDependencies += "org.scala-js" %% "scalajs-env-jsdom-nodejs" % "1.0.0"
           |""".stripMargin
      Try(IO.write(file / "project" / "plugins.sbt", pluginsSbt))
    }
  })
}

sbtLauncher := sbtLauncher.dependsOn(setPlugins).value
