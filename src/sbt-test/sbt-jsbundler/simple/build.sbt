import sbtjsbundler.sbtplugin.SbtJSBundlerPlugin
import org.scalajs.linker.interface.ModuleSplitStyle


version := "0.1"
scalaVersion := "3.3.1"

//enablePlugins(sbtplugin.SbtJSBundlerPlugin)
enablePlugins(ScalaJSPlugin, SbtJSBundlerPlugin)

scalaJSLinkerConfig ~= {
	_.withModuleKind(ModuleKind.ESModule)
	 .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("gsp")))
}
