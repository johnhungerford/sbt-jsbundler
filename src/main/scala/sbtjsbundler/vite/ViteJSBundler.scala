package sbtjsbundler.vite

import org.scalajs.sbtplugin.Stage
import sbt.Logger
import sbt.librarymanagement.Configuration
import sbtjsbundler.{DevServerProcess, JSBundler, NpmExecutor, NpmManager, ScopedJSBundler, SourceInjector}
import sbt.*

import scala.util.Try

final case class ViteJSBundler(
	config: ViteJSBundler.Config = ViteJSBundler.Config(),
) extends JSBundler { outerSelf =>
	private lazy val buildEnv =
		config.environment ++ config.buildEnvironment
	private lazy val devServEnv =
		config.environment ++ config.serverEnvironment ++ config.devServerEnvironment
	private lazy val previewEnv =
		config.environment ++ config.serverEnvironment ++ config.previewEnvironment
	private lazy val buildArgs =
		config.extraArgs ++ config.buildExtraArgs
	private lazy val devServArgs =
		config.extraArgs ++ config.serverExtraArgs ++ config.devServerExtraArgs
	private lazy val previewArgs =
		config.extraArgs ++ config.serverExtraArgs ++ config.previewExtraArgs

	override def scoped(
		configurationSources: Seq[sbt.File],
		scalaJsOutputDirectory: sbt.File,
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
			val configPath = viteConfigFile.getAbsolutePath
			val command = s"build -c $configPath --emptyOutDir" +: buildArgs
			NpmExecutor.run(
				"vite",
				"vite",
				command,
				buildEnv,
				Some(buildContextDirectory),
			)
		}

		override def startDevServer(): DevServerProcess = {
			val configPath = viteConfigFile.getAbsolutePath
			val command = s"node ./node_modules/vite/bin/vite.js -c $configPath " +
			  devServArgs.mkString(" ")
			val processBuilder = scala.sys.process.Process(command, buildContextDirectory, devServEnv.toSeq*)
			DevServerProcess(
				processBuilder.run()
			)
		}

		override def startPreview(): DevServerProcess = {
			val configPath = viteConfigFile.getAbsolutePath
			val command = s"node ./node_modules/vite/bin/vite.js preview -c $configPath " +
			  previewArgs.mkString(" ")
			val processBuilder = scala.sys.process.Process(command, buildContextDirectory, previewEnv.toSeq*)
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

object ViteJSBundler {
	final case class Config(
		environment: Map[String, String] = Map.empty[String, String],
		buildEnvironment: Map[String, String] = Map.empty[String, String],
		serverEnvironment: Map[String, String] = Map.empty[String, String],
		devServerEnvironment: Map[String, String] = Map.empty[String, String],
		previewEnvironment: Map[String, String] = Map.empty[String, String],
		extraArgs: Seq[String] = Nil,
		buildExtraArgs: Seq[String] = Nil,
		serverExtraArgs: Seq[String] = Nil,
		devServerExtraArgs: Seq[String] = Nil,
		previewExtraArgs: Seq[String] = Nil,
	) {
		def withEnv(vars: (String, String)*): Config = copy(environment = vars.toMap)
		def addEnv(vars: (String, String)*): Config = copy(environment = environment ++ vars)
		def withBuildEnv(vars: (String, String)*): Config = copy(buildEnvironment = vars.toMap)
		def addBuildEnv(vars: (String, String)*): Config = copy(buildEnvironment = buildEnvironment ++ vars)
		def withServerEnv(vars: (String, String)*): Config = copy(serverEnvironment = vars.toMap)
		def addServerEnv(vars: (String, String)*): Config = copy(serverEnvironment = serverEnvironment ++ vars)
		def withDevServerEnv(vars: (String, String)*): Config = copy(devServerEnvironment = vars.toMap)
		def addDevServerEnv(vars: (String, String)*): Config = copy(devServerEnvironment = devServerEnvironment ++ vars)
		def withPreviewEnv(vars: (String, String)*): Config = copy(previewEnvironment = vars.toMap)
		def addPreviewEnv(vars: (String, String)*): Config = copy(previewEnvironment = previewEnvironment ++ vars)

		def withArgs(args: String*): Config = copy(extraArgs = args)
		def addArgs(args: String*): Config = copy(extraArgs = extraArgs ++ args)
		def withBuildArgs(args: String*): Config = copy(buildExtraArgs = args)
		def addBuildArgs(args: String*): Config = copy(buildExtraArgs = buildExtraArgs ++ args)
		def withServerArgs(args: String*): Config = copy(serverExtraArgs = args)
		def addServerArgs(args: String*): Config = copy(serverExtraArgs = serverExtraArgs ++ args)
		def withDevServerArgs(args: String*): Config = copy(devServerExtraArgs = args)
		def addDevServerArgs(args: String*): Config = copy(devServerExtraArgs = devServerExtraArgs ++ args)
		def withPreviewArgs(args: String*): Config = copy(previewExtraArgs = args)
		def addPreviewArgs(args: String*): Config = copy(previewExtraArgs = previewExtraArgs ++ args)
	}
}
