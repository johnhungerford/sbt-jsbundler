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
		nonScalaSources: Seq[sbt.File],
		bundleOutputDirectory: sbt.File,
		buildContextDirectory: sbt.File,
		npmManager: NpmManager,
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
	protected def nonScalaSources: Seq[sbt.File]
	protected def bundleOutputDirectory: sbt.File
	protected def buildContextDirectory: sbt.File
	protected def npmManager: NpmManager
	protected def stage: Stage
	protected def configuration: sbt.librarymanagement.Configuration
	protected def logger: Logger

	def SCALAJS_BUILD_DIR_NAME: String = "__scalajs"
	def scalaJsBuildDir: sbt.File = buildContextDirectory / SCALAJS_BUILD_DIR_NAME

	def validateNonScalaSources: Either[String, Unit] =  Right(())

	def prepareConfiguration: Either[String, Unit]

	def prepareSources: Either[String, Unit] = for {
		_ <- SourceInjector.inject(nonScalaSources, buildContextDirectory)
		_ <- SourceInjector.inject(Seq(scalaJsOutputDirectory), scalaJsBuildDir)
	} yield ()

	def prepareBuildContext: Either[String, Unit] = for {
		_ <- prepareConfiguration
		_ <- prepareSources
	} yield ()

	def installNpmDependencies: Either[String, Unit] = {
		npmManager.install(Map.empty, Map.empty, Nil, Map.empty, Some(buildContextDirectory))
	}

	def buildBundle: Either[String, Unit]

	def startDevServer(): DevServerProcess

	def startPreview(): DevServerProcess

	def generateDevServerScript(outputDirectory: sbt.File, name: String): Either[String, Unit]

	def generatePreviewScript(outputDirectory: sbt.File, name: String): Either[String, Unit]
}

object ScopedJSBundler {
	final case class InputSource(directory: sbt.File, relativeEntryPoint: sbt.File) {
		require(!relativeEntryPoint.toPath.isAbsolute)

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
	def apply(process: Process): DevServerProcess = {
		() => Try {
			while (process.isAlive())
				process.destroy()
		}.toEither.left.map(_.toString)
	}
}
