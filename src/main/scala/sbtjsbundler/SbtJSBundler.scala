package sbtjsbundler

import org.scalajs.sbtplugin.Stage
import sbt.Logger
import sbt.librarymanagement.Configuration

import scala.util.Try

trait SbtJSBundler {
	/**
	 * Whether the plugin should track changes to non-Scala sources. False if
	 * this bundler implementation will reflect source changes in the output
	 * from [[ScopedSbtJSBundler.prepareBuildContext]].
	 *
	 * @return Scala.js plugin should track source changes
	 */
	def trackSourceChanges: Boolean

	/**
	 * Whether the plugin should track changes to config sources. False if
	 * this bundler implementation will reflect source changes in the output
	 * from [[ScopedSbtJSBundler.prepareBuildContext]].
	 *
	 * @return Scala.js plugin should track config changes
	 */
	def trackConfigSourceChanges: Boolean

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
	 * @param inputScriptRelativeToScalaJsOutputDirectory the script in Scala.js compiled outputs
	 *                                                    to be used as an entrypoint for the build
	 *                                                    (only used for bundling tests)
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
		inputScriptRelativeToScalaJsOutputDirectory: Option[sbt.File],
		stage: Stage,
		configuration: sbt.librarymanagement.Configuration,
		logger: Logger,
	): ScopedSbtJSBundler
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
 * @param inputScriptRelativeToScalaJsOutputDirectory the script in Scala.js compiled outputs
 *                                                    to be used as an entrypoint for the build
 *                                                    (only used for bundling tests)
 * @param stage whether to bundle in prod (fullLinkJS) or dev (fastLinkJS) mode
 * @param configuration what configuration this is scoped to (probably unnecessary, but some
 *                      implementations might want to behave differently for test and compile)
 * @param logger logger
 */
abstract class ScopedSbtJSBundler(
	protected val configurationSources: Seq[sbt.File],
	protected val scalaJsOutputDirectory: sbt.File,
	protected val nonScalaSources: Seq[sbt.File],
	protected val bundleOutputDirectory: sbt.File,
	protected val buildContextDirectory: sbt.File,
	protected val inputScriptRelativeToScalaJsOutputDirectory: Option[sbt.File],
	protected val stage: Stage,
	protected val configuration: sbt.librarymanagement.Configuration,
	protected val logger: Logger,
) {
	def outputScriptName: Option[String]

	def validateNonScalaSources: Either[String, Unit]

	def prepareConfiguration: Either[String, Boolean]

	def prepareSources: Either[String, Boolean]

	def prepareBuildContext: Either[String, Boolean] = for {
		configurationChanged <- prepareConfiguration
		sourcesChanged <- prepareSources
	} yield configurationChanged || sourcesChanged

	def build: Either[String, Unit]

	def startDevServer(): DevServerProcess

	def startPreview(): DevServerProcess

	def generateDevServerScript(outputDirectory: sbt.File, name: String): Either[String, Unit]

	def generatePreviewScript(outputDirectory: sbt.File, name: String): Either[String, Unit]
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

object NoOpBundler extends SbtJSBundler {

	/**
	 * Whether the plugin should track changes to non-Scala sources. False if
	 * this bundler implementation will reflect source changes in the output
	 * from [[ScopedSbtJSBundler.prepareBuildContext]].
	 *
	 * @return Scala.js plugin should track source changes
	 */
	override def trackSourceChanges: Boolean = false

	/**
	 * Whether the plugin should track changes to config sources. False if
	 * this bundler implementation will reflect source changes in the output
	 * from [[ScopedSbtJSBundler.prepareBuildContext]].
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
	 * @param inputScriptRelativeToScalaJsOutputDirectory the script in Scala.js compiled outputs
	 *                                                    to be used as an entrypoint for the build
	 *                                                    (only used for bundling tests)
	 * @param stage                                       whether to bundle in prod (fullLinkJS) or dev (fastLinkJS) mode
	 * @param configuration                               what configuration this is scoped to (probably unnecessary, but some
	 *                                                    implementations might want to behave differently for test and compile)
	 * @param logger                                      logger
	 * @return bundler implementation that can perform all the required tasks within the given scope
	 */
	override def scoped(
		configurationSources: Seq[sbt.File],
		scalaJsOutputDirectory: sbt.File,
		nonScalaSources: Seq[sbt.File],
		bundleOutputDirectory: sbt.File,
		buildContextDirectory: sbt.File,
		inputScriptRelativeToScalaJsOutputDirectory: Option[sbt.File],
		stage: Stage, configuration: Configuration,
		logger: Logger
	): ScopedSbtJSBundler = NoOpScopedBundler(
		configurationSources,
		scalaJsOutputDirectory,
		nonScalaSources,
		bundleOutputDirectory,
		buildContextDirectory,
		inputScriptRelativeToScalaJsOutputDirectory,
		stage,
		configuration,
		logger,
	)
}

final case class NoOpScopedBundler(
	override val configurationSources: Seq[sbt.File],
	override val scalaJsOutputDirectory: sbt.File,
	override val nonScalaSources: Seq[sbt.File],
	override val bundleOutputDirectory: sbt.File,
	override val buildContextDirectory: sbt.File,
	override val inputScriptRelativeToScalaJsOutputDirectory: Option[sbt.File],
	override val stage: Stage,
	override val configuration: sbt.librarymanagement.Configuration,
	override val logger: Logger,
) extends ScopedSbtJSBundler(
	configurationSources,
	scalaJsOutputDirectory,
	nonScalaSources,
	bundleOutputDirectory,
	buildContextDirectory,
	inputScriptRelativeToScalaJsOutputDirectory,
	stage,
	configuration,
	logger,
) {
	import scala.sys.process._

	override def outputScriptName: Option[String] = Some("main.js")

	override def validateNonScalaSources: Either[String, Unit] = ???

	override def prepareConfiguration: Either[String, Boolean] = {
		logger.info("prepareConfiguration")
		Right(true)
	}

	override def prepareSources: Either[String, Boolean] = {
		logger.info("prepareSources")
		logger.info(s"configurationSources: ${configurationSources}")
		logger.info(s"scalaJsOutputDirectory: ${scalaJsOutputDirectory}")
		logger.info(s"nonScalaSources: ${nonScalaSources}")
		logger.info(s"bundleOutputDirectory: ${bundleOutputDirectory}")
		logger.info(s"buildContextDirectory: ${buildContextDirectory}")
		logger.info(s"inputScriptRelativeToScalaJsOutputDirectory: ${inputScriptRelativeToScalaJsOutputDirectory}")
		logger.info(s"stage: ${stage}")
		logger.info(s"configuration: ${configuration}")
		logger.info(s"logger: ${logger}")
		Right(true)
	}

	override def build: Either[String, Unit] = {
		logger.info("build")

		Right(())
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
