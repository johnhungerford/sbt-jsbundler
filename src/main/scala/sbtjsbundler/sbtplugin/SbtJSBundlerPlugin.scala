package sbtjsbundler.sbtplugin

import org.scalajs.linker.interface.Report
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.{fastLinkJS, fullLinkJS, jsEnvInput, scalaJSLinkerOutputDirectory}
import org.scalajs.sbtplugin.{ScalaJSPlugin, Stage}
import sbt.*
import sbt.Keys.{baseDirectory, configuration, crossTarget, state, streams}
import sbt.plugins.JvmPlugin
import sbtjsbundler.{DevServerProcess, NoOpBundler, SbtJSBundler}
import sbtjsbundler.sbtplugin.SbtJSBundlerPlugin.autoImport.{bundlerBuildDirectory, bundlerConfigSources, bundlerImplementation, bundlerManagedSources, bundlerOutputDirectory, bundlerTargetDirectory, prepareBundle}
import org.scalajs.jsenv.Input
import org.scalajs.jsenv.Input.Script


object SbtJSBundlerPlugin extends AutoPlugin {

  override def trigger = allRequirements
  override def requires = ScalaJSPlugin

  object autoImport {

    // PUBLIC SETTINGS

    val bundlerImplementation = settingKey[SbtJSBundler]("JS bundler implementation")

    val bundlerManagedSources = settingKey[Seq[File]]("Non-Scala sources to be bundled with Scala.js outputs")

    val bundlerConfigSources = settingKey[Seq[File]]("Configuration sources for the selected JS bundler")

    val bundlerOutputDirectory = settingKey[File]("Where to persist JS bundles")

    val bundlerServerScriptDirectory = settingKey[File]("Where to output the bundler's dev server script")

    val bundlerDevServerScriptName = settingKey[String]("Name to give the bundler's dev server script")

    val bundlerPreviewScriptName = settingKey[String]("Name to give the bundler's preview script")


    // PUBLIC TASKS

    val prepareBundle = taskKey[Boolean]("Prepare code and configuration for bundling")

    val bundle = taskKey[Unit]("Bundle Scala.js code with dependencies")

    val startDevServer = taskKey[Unit]("Start the bundler's dev server")

    val stopDevServer = taskKey[Unit]("Stop the bundler's dev server")

    val generateDevServerScript = taskKey[Unit]("Generate a script to run the dev server")

    val startPreview = taskKey[Unit]("Start the preview server")

    val stopPreview = taskKey[Unit]("Stop the preview server")

    val generatePreviewScript = taskKey[Unit]("Generate a script to run the preview server")

    // PRIVATE SETTINGS

    private[sbtjsbundler] val bundlerBuildDirectory = settingKey[File]("Where to keep build context")

    private[sbtjsbundler] val bundlerTargetDirectory = settingKey[File]("Root directory for all bundler artifacts")

    // PRIVATE TASKS

    private[sbtjsbundler] val startDevServerProcess = taskKey[DevServerProcess]("Start the dev server")

    private[sbtjsbundler] val startPreviewProcess = taskKey[DevServerProcess]("Start the preview server")
  }

  import autoImport._

  private def scopedBundler(
    config: Configuration,
    jsScope: TaskKey[sbt.Attributed[Report]],
  ) = Def.task {
    val bundler = (config / jsScope / bundlerImplementation).value

    val inputScript = if (config == Test) Some(file("main.js")) else None

    val stage = if (jsScope == fullLinkJS) Stage.FullOpt else Stage.FastOpt

    val log = streams.value.log

    bundler.scoped(
      (config / jsScope / bundlerConfigSources).value,
      (config / jsScope / scalaJSLinkerOutputDirectory).value,
      (config / jsScope / bundlerManagedSources).value,
      (config / jsScope / bundlerOutputDirectory).value,
      (config / jsScope / bundlerBuildDirectory).value,
      inputScript,
      stage,
      config,
      log,
    )
  }

  private def jsScopeFromStage(stage: Stage): TaskKey[Attributed[Report]] = stage match {
    case Stage.FullOpt => fullLinkJS
    case Stage.FastOpt => fastLinkJS
  }

  private def backgroundTask(
    config: Configuration,
    jsScope: TaskKey[_],
    processTask: TaskKey[DevServerProcess],
    startTask: TaskKey[Unit],
    stopTask: TaskKey[Unit],
  ) = {
    var _process: Option[DevServerProcess] = None
    Seq(
      config / jsScope / startTask := {
        val process = (config / jsScope / processTask).value
        _process = Some(process)
      },
      config / jsScope / stopTask := {
        _process.foreach(_.shutDown())
        _process = None
      }
    )
  }

  private def perConfigSettings(config: Configuration, stage: Stage) = {
    val jsScope = jsScopeFromStage(stage)

    val targetSubdirBase = if (stage == Stage.FullOpt) "jsbundler-opt" else "jsbundler-fastopt"
    val targetSubdir = if (config == Test) s"$targetSubdirBase-test" else targetSubdirBase

    Seq(
      config / jsScope / bundlerManagedSources := bundlerManagedSources.value,
      config / jsScope / bundlerConfigSources := bundlerConfigSources.value,
      config / jsScope / bundlerImplementation := bundlerImplementation.value,

      config / jsScope / bundlerTargetDirectory := (config / jsScope / crossTarget).value / targetSubdir,
      config / jsScope / bundlerBuildDirectory := (config / jsScope / bundlerTargetDirectory).value / "build",
      config / jsScope / bundlerOutputDirectory := (config / jsScope / bundlerTargetDirectory).value / "dist",

      config / jsScope / prepareBundle := {
        val bundler = scopedBundler(config, jsScope).value
        bundler.prepareBuildContext match {
          case Left(message) =>
            throw new MessageOnlyException(s"Bundler failed to prepare build context: $message")
          case Right(value) => value
        }
      },

      config / jsScope / prepareBundle :=
        (config / jsScope / prepareBundle).dependsOn(config / jsScope).value,

      config / jsScope / bundle := {
        val bundler = scopedBundler(config, jsScope).value
        bundler.build match {
          case Left(message) =>
            throw new MessageOnlyException(s"Bundler failed to build: $message")
          case Right(value) => value
        }
      },

      config / jsScope / bundle :=
        (config / jsScope / bundle).dependsOn(config / jsScope / prepareBundle).value,

      config / jsScope / startDevServerProcess := {
        val bundler = scopedBundler(config, jsScope).value
        bundler.startDevServer()
      },
    )
  }

  private val allPerConfigSettings = (for {
    config <- List(Compile, Test)
    stage <- List(Stage.FastOpt, Stage.FullOpt)
    setting <- perConfigSettings(config, stage)
  } yield setting)

  override lazy val projectSettings = Seq(
    bundlerManagedSources := Nil,
    bundlerConfigSources := Nil,
    bundlerImplementation := NoOpBundler,

    prepareBundle := (Compile / fullLinkJS / prepareBundle).value,
    bundle := (Compile / fullLinkJS / bundle).value,
    startDevServer := (Compile / fastLinkJS / startDevServer).value,
    stopDevServer := (Compile / fastLinkJS / stopDevServer).value,
    startPreview := (Compile / fullLinkJS / startPreview).value,
    stopPreview := (Compile / fullLinkJS / stopPreview).value,

    Test / prepareBundle := (Test / fastLinkJS / prepareBundle).value,
    Test / bundle := (Test / fastLinkJS / bundle).value,

    Compile / fastLinkJS / startDevServerProcess := {
      val bundler = scopedBundler(Compile, fastLinkJS).value
      bundler.startPreview()
    },

    Compile / fastLinkJS / startDevServerProcess :=
      (Compile / fastLinkJS / startDevServerProcess).dependsOn(Compile / fastLinkJS / prepareBundle).value,

    Compile / fullLinkJS / startPreviewProcess := {
      val bundler = scopedBundler(Compile, fullLinkJS).value
      bundler.startPreview()
    },

    Compile / fullLinkJS / startPreviewProcess :=
      (Compile / fullLinkJS / startPreviewProcess).dependsOn(Compile / fullLinkJS / bundle).value,

    Compile / fastLinkJS / bundlerServerScriptDirectory :=
      baseDirectory.value,

    Compile / fastLinkJS / bundlerDevServerScriptName := "start-dev-server",

    Compile / fastLinkJS / generateDevServerScript := {
      val bundler = scopedBundler(Compile, fastLinkJS).value
      val outputDirectory = (Compile / fastLinkJS / bundlerServerScriptDirectory).value
      val scriptName = (Compile / fastLinkJS / bundlerDevServerScriptName).value
      bundler.generateDevServerScript(outputDirectory, scriptName)
    },

    Compile / fullLinkJS / bundlerServerScriptDirectory :=
      baseDirectory.value,

    Compile / fullLinkJS / bundlerDevServerScriptName := "start-dev-server-prod",

    Compile / fullLinkJS / generateDevServerScript := {
      val bundler = scopedBundler(Compile, fullLinkJS).value
      val outputDirectory = (Compile / fullLinkJS / bundlerServerScriptDirectory).value
      val scriptName = (Compile / fullLinkJS / bundlerDevServerScriptName).value
      bundler.generateDevServerScript(outputDirectory, scriptName)
    },

    Compile / fullLinkJS / bundlerPreviewScriptName := "start-preview",

    Compile / fullLinkJS / generatePreviewScript := {
      val bundler = scopedBundler(Compile, fullLinkJS).value
      val outputDirectory = (Compile / fullLinkJS / bundlerServerScriptDirectory).value
      val scriptName = (Compile / fullLinkJS / bundlerPreviewScriptName).value
      bundler.generatePreviewScript(outputDirectory, scriptName)
    },

    Compile / fastLinkJS / bundlerPreviewScriptName := "start-preview-dev",

    Compile / fastLinkJS / generatePreviewScript := {
      val bundler = scopedBundler(Compile, fastLinkJS).value
      val outputDirectory = (Compile / fastLinkJS / bundlerServerScriptDirectory).value
      val scriptName = (Compile / fastLinkJS / bundlerDevServerScriptName).value
      bundler.generatePreviewScript(outputDirectory, scriptName)
    },

    generateDevServerScript := (Compile / fastLinkJS / generateDevServerScript).value,

    generatePreviewScript := (Compile / fullLinkJS / generatePreviewScript).value,

    Compile / jsEnvInput := {
      val bundler = scopedBundler(Compile, fastLinkJS).value
      val outputDirectory = (Compile / fastLinkJS / bundlerOutputDirectory).value.toPath
      bundler.outputScriptName.map { scriptName =>
        Input.Script(outputDirectory / scriptName)
      } match {
        case Some(input) => Seq(input)
        case None => (Compile / jsEnvInput).value
      }
    },

    Test / jsEnvInput := {
      val bundler = scopedBundler(Test, fastLinkJS).value
      val outputDirectory = (Test / fastLinkJS / bundlerOutputDirectory).value.toPath
      bundler.outputScriptName.map { scriptName =>
        Input.Script(outputDirectory / scriptName)
      } match {
        case Some(input) => Seq(input)
        case None => (Test / jsEnvInput).value
      }
    },
  ) ++
    backgroundTask(
      Compile,
      fastLinkJS,
      startDevServerProcess,
      startDevServer,
      stopDevServer,
    ) ++
    backgroundTask(
      Compile,
      fullLinkJS,
      startPreviewProcess,
      startPreview,
      stopPreview,
    ) ++
    allPerConfigSettings
}
