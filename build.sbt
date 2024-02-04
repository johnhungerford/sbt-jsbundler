import scala.util.Try

name := """sbt-jsbundler"""
organization := "io.github.johnhungerford"
version := "0.1-SNAPSHOT"

sbtPlugin := true

inThisBuild(List(
  organization := "io.github.johnhungerford",
  homepage := Some(url("https://github.com/johnhungerford/sbt-jsbundler")),
  licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  developers := List(
    Developer(
      "johnhungerford",
      "John Hungerford",
      "hungerfordjustice@gmail.com",
      url("https://johnhungerford.github.io")
    )
  )
))

console / initialCommands := """import sbtjsbundler._"""

enablePlugins(ScriptedPlugin)
// set up 'scripted; sbt plugin for testing sbt plugins
scriptedLaunchOpts ++=
  Seq("-Xmx1024M")

ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches :=
  Seq(RefPredicate.StartsWith(Ref.Tag("v")))

ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Sbt(
    List("ci-release"),
    env = Map(
      "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
      "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
    )
  )
)

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.15.0")

val setPlugins = taskKey[Unit]("Inject plugins into scripted test projects with correct version")

setPlugins := {
  IO.listFiles(file("src/sbt-test/sbt-jsbundler")).toList.foreach(file => {
    if (file.isDirectory) {
      val pluginsSbt =
        s"""addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.15.0")
           |addSbtPlugin("io.github.johnhungerford" % "sbt-jsbundler" % "${version.value}")
           |""".stripMargin
      Try(IO.write(file / "project" / "plugins.sbt", pluginsSbt))
    }
  })
}
sbtLauncher := sbtLauncher.dependsOn(setPlugins).value
