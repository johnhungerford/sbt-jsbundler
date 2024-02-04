import org.scalajs.linker.interface.ModuleSplitStyle
import org.scalajs.sbtplugin.Stage

version := "0.1"
scalaVersion := "3.3.1"

libraryDependencies += "com.lihaoyi" %%% "utest" % "0.8.2" % Test
libraryDependencies ++= Seq(
	"io.github.cquiroz" %%% "scala-java-time" % "2.5.0" % Test,
	"io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.5.0" % Test,
)
libraryDependencies += "com.github.japgolly.scalajs-react" %%% "core" % "2.1.1"
libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "2.2.0"

enablePlugins(ScalaJSPlugin, JSBundlerPlugin)

bundlerImplementation := sbtjsbundler.vite.ViteJSBundler()

scalaJSLinkerConfig ~= {
	_.withModuleKind(ModuleKind.ESModule)
//	 .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("gsp")))
}

bundlerManagedSources ++= Seq(
	file("src/main/javascript"),
	file("src/main/styles"),
	file("public"),
	file("jsbundler")
)

testFrameworks += new TestFramework("utest.runner.Framework")
