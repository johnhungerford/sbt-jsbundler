package sbtjsbundler.vite

import org.scalajs.sbtplugin.Stage
import sbt.Logger
import sbt.librarymanagement.Configuration
import sbtjsbundler.{DevServerProcess, JSBundler, NpmExecutor, NpmManager, ScopedJSBundler, SourceInjector}
import sbt.*

import scala.util.Try

final case class ViteJSBundler(
	private val prodEnvironment: Map[String, String] = Map.empty[String, String],
	private val devEnvironment: Map[String, String] = Map.empty[String, String],
	private val prodExtraArgs: Seq[String] = Nil,
	private val devExtraArgs: Seq[String] = Nil,
) extends JSBundler { outerSelf =>
	def addEnv(varDef: (String, String)*): ViteJSBundler = {
		copy(
			prodEnvironment = prodEnvironment ++ varDef,
			devEnvironment = devEnvironment ++ varDef,
		)
	}

	def addProdEnvVariable(varDef: (String, String)*): ViteJSBundler = {
		copy(
			prodEnvironment = prodEnvironment ++ varDef,
		)
	}

	def addDevEnvVariable(varDef: (String, String)*): ViteJSBundler = {
		copy(
			devEnvironment = devEnvironment ++ varDef,
		)
	}

	def addExtraArg(arg: String*): ViteJSBundler = {
		copy(prodExtraArgs = prodExtraArgs ++ arg, devExtraArgs = devExtraArgs ++ arg)
	}

	def addProdExtraArg(arg: String*): ViteJSBundler = {
		copy(prodExtraArgs = prodExtraArgs ++ arg)
	}

	def addDevExtraArg(arg: String*): ViteJSBundler = {
		copy(devExtraArgs = devExtraArgs ++ arg)
	}

	override def scoped(
		configurationSources: Seq[sbt.File],
		scalaJsOutputDirectory: sbt.File,
		jsInputSource: Option[ScopedJSBundler.InputSource],
		nonScalaSources: Seq[sbt.File],
		bundleOutputDirectory: sbt.File,
		buildContextDirectory: sbt.File,
		npmManager: NpmManager,
		stage: Stage,
		configuration: Configuration,
		logger: Logger,
	): Scoped = Scoped(
		configurationSources,
		scalaJsOutputDirectory,
		jsInputSource,
		nonScalaSources,
		bundleOutputDirectory,
		buildContextDirectory,
		npmManager,
		stage,
		configuration,
		logger,
	)

	case class Scoped(
		override val configurationSources: Seq[sbt.File],
		override val scalaJsOutputDirectory: sbt.File,
		override val jsInputSource: Option[ScopedJSBundler.InputSource],
		override val nonScalaSources: Seq[sbt.File],
		override val bundleOutputDirectory: sbt.File,
		override val buildContextDirectory: sbt.File,
		override val npmManager: NpmManager,
		override val stage: Stage,
		override val configuration: Configuration,
		override val logger: Logger,
	) extends ScopedJSBundler {
		private val resourceDirectory: sbt.File = buildContextDirectory / "__vite"
		private val viteConfigFile: sbt.File = resourceDirectory / "vite.config-generated.js"
		private val viteCustomPluginFile: sbt.File = resourceDirectory / "customVitePlugin.ts"

		override def prepareConfiguration: Either[String, Unit] = {
			IO.createDirectory(resourceDirectory)
			SourceInjector.inject(configurationSources, resourceDirectory)

			val requiredImports = List(
				"""import _ from 'lodash'""",
				"""import { defineConfig } from "vite";""",
				"""import scalaJSPlugin from "./customVitePlugin";"""
			) ++ {
				if (stage == Stage.FastOpt)
					List("""import sourcemaps from 'rollup-plugin-sourcemaps';""")
				else Nil
			}

			val scalajsPath =
				scalaJsBuildDir
				  .relativeTo(
					  buildContextDirectory
				  ).get.toPath.toString

			val plugins = List(
				s"""scalaJSPlugin('$scalajsPath')""",
			)

			val rollupPlugins = stage match {
				case Stage.FastOpt => List("""sourcemaps()""")
				case Stage.FullOpt => Nil
			}

			for {
				configString <- ViteConfigGen.generate(
					configurationSources.map(v => v.getName).toList,
					buildContextDirectory.getAbsolutePath,
					jsInputSource.map(
						_.entryPointFrom(
							inputBuildDir.relativeTo(buildContextDirectory).get,
						).toPath.toString
					),
					bundleOutputDirectory.getAbsolutePath,
					requiredImports,
					plugins,
					rollupPlugins,
					stage == Stage.FastOpt,
				).left.map(v => s"Unable to generate vite configuration: $v")
				customPluginString <- Try(
					scala.io.Source.fromInputStream(
						getClass.getClassLoader.getResourceAsStream("customVitePlugin.ts")
					).mkString
				).toEither.left.map(v => s"Unable to retrieve custom vite plugin from resources: $v")
				_ <- Try(IO.createDirectory(resourceDirectory))
				  .toEither.left.map(v => s"Unable to create vite resources directory: $v")
				_ <- Try(IO.write(viteConfigFile, configString))
				  .toEither.left.map(v => s"Unable to write vite configuration to file: $v")
				_ <- Try(IO.write(viteCustomPluginFile, customPluginString))
				  .toEither.left.map(v => s"Unable to write custom vite plugin to file: $v")
			} yield ()
		}

		override def buildBundle: Either[String, Unit] = {
			val (extraArgs, environment) = stage match {
				case Stage.FastOpt => devExtraArgs -> devEnvironment
				case Stage.FullOpt => prodExtraArgs -> prodEnvironment
			}
			val configPath = viteConfigFile.getAbsolutePath
			val command = s"build -c $configPath --emptyOutDir" +: extraArgs
			NpmExecutor.run(
				"vite",
				"vite",
				command,
				environment,
				Some(buildContextDirectory),
			)
		}

		override def startDevServer(): DevServerProcess = {
			val (extraArgs, environment) = stage match {
				case Stage.FastOpt => devExtraArgs -> devEnvironment
				case Stage.FullOpt => prodExtraArgs -> prodEnvironment
			}
			val configPath = viteConfigFile.getAbsolutePath
			val command = s"node ./node_modules/vite/bin/vite.js -c $configPath " +
			  extraArgs.mkString(" ")
			val processBuilder = scala.sys.process.Process(command, buildContextDirectory, environment.toSeq*)
			DevServerProcess(
				processBuilder.run()
			)
		}

		override def startPreview(): DevServerProcess = {
			val (extraArgs, environment) = stage match {
				case Stage.FastOpt => devExtraArgs -> devEnvironment
				case Stage.FullOpt => prodExtraArgs -> prodEnvironment
			}
			val configPath = viteConfigFile.getAbsolutePath
			val command = s"node ./node_modules/vite/bin/vite.js preview -c $configPath " +
			  extraArgs.mkString(" ")
			val processBuilder = scala.sys.process.Process(command, buildContextDirectory, environment.toSeq*)
			DevServerProcess(
				processBuilder.run()
			)
		}

		override def generateDevServerScript(outputDirectory: sbt.File, name: String): Either[String, Unit] = {
			val scriptFile = outputDirectory / name.stripSuffix(".sh").+(".sh")
			for {
				script <- ViteScriptGen.generateDevServerScript(viteConfigFile, buildContextDirectory)
				_ <- Try(IO.write(scriptFile, script))
				  .toEither.left.map(v => s"Unable to write dev server script to $outputDirectory: $v")
				_ <- Try(IO.chmod("rwxr-x---", scriptFile))
				  .toEither.left.map(v => s"Unable to make dev server script executable: $v")
			} yield ()
		}

		override def generatePreviewScript(outputDirectory: sbt.File, name: String): Either[String, Unit] = {
			val scriptFile = outputDirectory / name.stripSuffix(".sh").+(".sh")
			for {
				script <- ViteScriptGen.generatePreviewScript(viteConfigFile, buildContextDirectory)
				_ <- Try(IO.write(scriptFile, script))
				  .toEither.left.map(v => s"Unable to write preview script to $outputDirectory: $v")
				_ <- Try(IO.chmod("rwxr-x---", scriptFile))
				  .toEither.left.map(v => s"Unable to make preview script executable: $v")
			} yield ()
		}
	}
}
