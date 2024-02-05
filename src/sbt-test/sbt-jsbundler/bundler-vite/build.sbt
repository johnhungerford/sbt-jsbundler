import org.scalajs.linker.interface.ModuleSplitStyle
import org.scalajs.sbtplugin.Stage
import org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv
import sbtjsbundler.{NpmManager, PnpmNpmManager, YarnNpmManager}

version := "0.1"
scalaVersion := "3.3.1"

enablePlugins(ScalaJSPlugin, JSBundlerPlugin)

fastLinkJS / bundlerImplementation := sbtjsbundler.vite.ViteJSBundler(
	sbtjsbundler.vite.ViteJSBundler.Config()
		.addEnv("NODE_ENV" -> "development")
)

bundlerManagedSources ++= Seq(
	file("src/main/javascript"),
	file("src/main/styles"),
	file("jsbundler")
)

libraryDependencies ++= Seq(
	// Scala.js API for React
	"com.github.japgolly.scalajs-react" %%% "core" % "2.1.1",
	// Scala.js DOM API
	"org.scala-js" %%% "scalajs-dom" % "2.2.0",

	// Testing framework
	"com.lihaoyi" %%% "utest" % "0.8.2" % Test,

	// Libraries for testing react in Scala.js
	"com.github.japgolly.test-state" %%% "core"              % "3.1.0" % Test,
	"com.github.japgolly.test-state" %%% "dom-zipper"        % "3.1.0" % Test,
	"com.github.japgolly.test-state" %%% "dom-zipper-sizzle" % "3.1.0" % Test,
	"com.github.japgolly.test-state" %%% "ext-scalajs-react" % "3.1.0" % Test,

	// JS implementation of Java time lib needed for test-state
	"io.github.cquiroz" %%% "scala-java-time" % "2.5.0" % Test,
	"io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.5.0" % Test,
)

testFrameworks += new TestFramework("utest.runner.Framework")

// Test configuration with Vite
Test / jsEnv :=
	new JSDOMNodeJSEnv(
		JSDOMNodeJSEnv.Config()
			.withArgs(List("-r", "source-map-support/register"))
	)


// Install npm dependencies needed for JSDOMNodeJSEnv on startup:
lazy val installDeps = taskKey[Unit]("Install npm dependencies on startup")

installDeps := {
	import scala.sys.process.*
	val rootFile = file(".")
	Process("rm -rf node_modules", Some(rootFile)).run().exitValue()
	Process("npm install", Some(rootFile)).run().exitValue()
}

lazy val startupTransition: State => State = { s: State =>
	"installDeps" :: s
}

Global / onLoad := {
	val old = (Global / onLoad).value
	// compose the new transition on top of the existing one
	// in case your plugins are using this hook.
	startupTransition compose old
}
