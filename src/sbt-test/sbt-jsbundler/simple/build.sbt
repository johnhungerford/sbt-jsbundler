import sbtjsbundler.sbtplugin.JSBundlerPlugin
import org.scalajs.linker.interface.ModuleSplitStyle


version := "0.1"
scalaVersion := "3.3.1"

//enablePlugins(sbtplugin.SbtJSBundlerPlugin)
enablePlugins(ScalaJSPlugin, JSBundlerPlugin)

scalaJSLinkerConfig ~= {
	_.withModuleKind(ModuleKind.ESModule)
	 .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("gsp")))
}

scalaJSUseMainModuleInitializer := true

bundlerImplementation := sbtjsbundler.vite.ViteJSBundler()

bundlerManagedSources ++= Seq(
	file("js-build-resources"),
	file("src/main/javascript"),
)
