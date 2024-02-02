package sbtjsbundler

import org.scalajs.sbtplugin.Stage
import sbt.Logger
import sbt.librarymanagement.Configuration

import scala.util.Try

import sbt._

trait JSBundler {
	/**
	 * Whether the plugin should track changes to non-Scala sources. False if
	 * this bundler implementation will reflect source changes in the output
	 * from [[ScopedJSBundler.prepareBuildContext]].
	 *
	 * @return Scala.js plugin should track source changes
	 */
	def trackSourceChanges: Boolean = true

	/**
	 * Whether the plugin should track changes to config sources. False if
	 * this bundler implementation will reflect source changes in the output
	 * from [[ScopedJSBundler.prepareBuildContext]].
	 *
	 * @return Scala.js plugin should track config changes
	 */
	def trackConfigSourceChanges: Boolean = true

	/**
	 * Construct a bundler implementation within a scope of possibly relevant parameters.
	 *
	 * @param configurationSources configuration files to be used to generate the final
	 *                             configuration for this bundler within this scope
	 * @param scalaJsOutputDirectory where compiled Scala.js code can be found to be bundled
	 *                               with other sources
	 * @param jsInputSource the script in Scala.js compiled outputs to be used as an entrypoint
	 *                      for the build (only used for bundling tests)
	 * @param nonScalaSources a flat list of all sources aside from Scala to be included in build
	 * @param bundleOutputDirectory directory where the bundle should go
	 * @param buildContextDirectory working directory for the bundler
	 * @param stage whether to bundle in prod (fullLinkJS) or dev (fastLinkJS) mode
	 * @param configuration what configuration this is scoped to (probably unnecessary, but some
	 *                      implementations might want to behave differently for test and compile)
	 * @param logger logger
	 * @return bundler implementation that can perform all the required tasks within the given scope
	 */
	def scoped(
		configurationSources: Seq[sbt.File],
		scalaJsOutputDirectory: sbt.File,
		jsInputSource: Option[ScopedJSBundler.InputSource],
		nonScalaSources: Seq[sbt.File],
		bundleOutputDirectory: sbt.File,
		buildContextDirectory: sbt.File,
		npmExecutor: NpmExecutor,
		stage: Stage,
		configuration: sbt.librarymanagement.Configuration,
		logger: Logger,
	): ScopedJSBundler
}

/**
 * Implementation of a JS Bundler within the scope of the abstract constructor parameters
 *
 * @param configurationSources configuration files to be used to generate the final
 *                             configuration for this bundler within this scope
 * @param scalaJsOutputDirectory where compiled Scala.js code can be found to be bundled
 *                               with other sources
 * @param jsInputSource the source directory and script within it to be used as an entrypoint to
 *                      the bundle. typically only used for tests
 * @param nonScalaSources a flat list of all sources aside from Scala to be included in build
 * @param bundleOutputDirectory directory where the bundle should go
 * @param buildContextDirectory working directory for the bundler
 * @param stage whether to bundle in prod (fullLinkJS) or dev (fastLinkJS) mode
 * @param configuration what configuration this is scoped to (probably unnecessary, but some
 *                      implementations might want to behave differently for test and compile)
 * @param logger logger
 */
abstract class ScopedJSBundler {
	protected def configurationSources: Seq[sbt.File]
	protected def scalaJsOutputDirectory: sbt.File
	protected def jsInputSource: Option[ScopedJSBundler.InputSource]
	protected def nonScalaSources: Seq[sbt.File]
	protected def bundleOutputDirectory: sbt.File
	protected def buildContextDirectory: sbt.File
	protected def npmExecutor: NpmExecutor
	protected def stage: Stage
	protected def configuration: sbt.librarymanagement.Configuration
	protected def logger: Logger

	def SCALAJS_BUILD_DIR_NAME: String = "__scalajs"
	def INPUT_BUILD_DIR_NAME: String = "__input"
	def scalaJsBuildDir: sbt.File = buildContextDirectory / SCALAJS_BUILD_DIR_NAME
	def inputBuildDir: sbt.File = buildContextDirectory / INPUT_BUILD_DIR_NAME

	def outputScript: Option[sbt.File] = jsInputSource
	  .map(_.entryPointFrom(bundleOutputDirectory))

	def validateNonScalaSources: Either[String, Unit] =  Right(())

	def prepareConfiguration: Either[String, Unit]

	def prepareSources: Either[String, Unit] = for {
		_ <- Try(IO.createDirectories(Seq(scalaJsBuildDir, inputBuildDir)))
		  .toEither.left.map { v =>
			  s"Unexpected error while creating scalajs and input directories in bundler build context: $v"
		  }
		_ <- Right {
			println(s"scalaJsOutputDirectory: $scalaJsOutputDirectory")
			println(s"scalaJsBuildDir: $scalaJsBuildDir")
		}
		_ <- SourceInjector.inject(nonScalaSources, buildContextDirectory)
		_ <- SourceInjector.inject(Seq(scalaJsOutputDirectory), scalaJsBuildDir)
		_ <- jsInputSource match {
			case Some(ScopedJSBundler.InputSource(inputDir, _)) =>
				SourceInjector.inject(Seq(inputDir), inputBuildDir)
			case None => Right(())
		}
	} yield ()

	def prepareBuildContext: Either[String, Unit] = for {
		_ <- prepareConfiguration
		_ <- prepareSources
	} yield ()

	def buildBundle: Either[String, Unit]

	def installNpmDependencies: Either[String, Unit] = {
		npmExecutor.install(Map.empty, Map.empty, Nil, Map.empty, Some(buildContextDirectory))
	}

	def installAndBuild: Either[String, Unit] = for {
		_ <- installNpmDependencies
		_ <- buildBundle
	} yield ()

	def startDevServer(): DevServerProcess

	def startPreview(): DevServerProcess

	def generateDevServerScript(outputDirectory: sbt.File, name: String): Either[String, Unit]

	def generatePreviewScript(outputDirectory: sbt.File, name: String): Either[String, Unit]
}

object ScopedJSBundler {
	final case class InputSource(directory: sbt.File, relativeEntryPoint: sbt.File) {
		require(!relativeEntryPoint.toPath.isAbsolute)
		require(directory.isDirectory)

		def entryPointFrom(newDirectory: sbt.File): sbt.File = {
			newDirectory.toPath.resolve(relativeEntryPoint.toPath).toFile
		}
	}
}

trait DevServerProcess {
	def shutDown(): Either[String, Unit]
}

object DevServerProcess {
	import scala.sys.process._
	def apply(process: Process): DevServerProcess = new DevServerProcess {
		override def shutDown(): Either[String, Unit] =
			Try(process.destroy()).toEither.left.map(_.toString)
	}
}

object NoOpBundler extends JSBundler {

	/**
	 * Whether the plugin should track changes to non-Scala sources. False if
	 * this bundler implementation will reflect source changes in the output
	 * from [[ScopedJSBundler.prepareBuildContext]].
	 *
	 * @return Scala.js plugin should track source changes
	 */
	override def trackSourceChanges: Boolean = false

	/**
	 * Whether the plugin should track changes to config sources. False if
	 * this bundler implementation will reflect source changes in the output
	 * from [[ScopedJSBundler.prepareBuildContext]].
	 *
	 * @return Scala.js plugin should track config changes
	 */
	override def trackConfigSourceChanges: Boolean = false

	/**
	 * Construct a bundler implementation within a scope of possibly relevant parameters.
	 *
	 * @param configurationSources                        configuration files to be used to generate the final
	 *                                                    configuration for this bundler within this scope
	 * @param scalaJsOutputDirectory                      where compiled Scala.js code can be found to be bundled
	 *                                                    with other sources
	 * @param nonScalaSources                             a flat list of all sources aside from Scala to be included in build
	 * @param bundleOutputDirectory                       directory where the bundle should go
	 * @param buildContextDirectory                       working directory for the bundler
	 * @param stage                                       whether to bundle in prod (fullLinkJS) or dev (fastLinkJS) mode
	 * @param configuration                               what configuration this is scoped to (probably unnecessary, but some
	 *                                                    implementations might want to behave differently for test and compile)
	 * @param logger                                      logger
	 * @return bundler implementation that can perform all the required tasks within the given scope
	 */
	override def scoped(
		configurationSources: Seq[sbt.File],
		scalaJsOutputDirectory: sbt.File,
		jsInputSource: Option[ScopedJSBundler.InputSource],
		nonScalaSources: Seq[sbt.File],
		bundleOutputDirectory: sbt.File,
		buildContextDirectory: sbt.File,
		npmExecutor: NpmExecutor,
		stage: Stage, configuration: Configuration,
		logger: Logger
	): ScopedJSBundler = NoOpScopedBundler(
		configurationSources,
		scalaJsOutputDirectory,
		jsInputSource: Option[ScopedJSBundler.InputSource],
		nonScalaSources,
		bundleOutputDirectory,
		buildContextDirectory,
		npmExecutor,
		stage,
		configuration,
		logger,
	)
}

final case class NoOpScopedBundler(
	override val configurationSources: Seq[sbt.File],
	override val scalaJsOutputDirectory: sbt.File,
	override val jsInputSource: Option[ScopedJSBundler.InputSource],
	override val nonScalaSources: Seq[sbt.File],
	override val bundleOutputDirectory: sbt.File,
	override val buildContextDirectory: sbt.File,
	override val npmExecutor: NpmExecutor,
	override val stage: Stage,
	override val configuration: sbt.librarymanagement.Configuration,
	override val logger: Logger,
) extends ScopedJSBundler {
	import scala.sys.process._

	override def prepareConfiguration: Either[String, Unit] = Right {
		logger.info("prepareConfiguration")
	}

	override def buildBundle: Either[String, Unit] = Right {
		logger.info("buildBundle")
	}

	override def startDevServer(): DevServerProcess = {
		DevServerProcess(Process("""echo "startDevServer()"""").run())
	}

	override def startPreview(): DevServerProcess = {
		DevServerProcess(Process("""echo "startPreview()"""").run())
	}

	override def generateDevServerScript(outputDirectory: sbt.File, name: String): Either[String, Unit] = {
		logger.info(s"generateDevServerScript: ${outputDirectory}, ${name}")
		Right(())
	}

	override def generatePreviewScript(outputDirectory: sbt.File, name: String): Either[String, Unit] = {
		logger.info(s"generatePreviewScript: ${outputDirectory}, ${name}")
		Right(())
	}
}
