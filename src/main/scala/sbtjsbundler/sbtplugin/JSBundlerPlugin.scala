package sbtjsbundler.sbtplugin

import org.scalajs.jsenv.Input
import org.scalajs.linker.interface.Report
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.{fastLinkJS, fullLinkJS, jsEnvInput, scalaJSLinkerOutputDirectory}
import org.scalajs.sbtplugin.{ScalaJSPlugin, Stage}
import sbt.*
import sbt.Keys.{baseDirectory, crossTarget, streams, test}
import sbt.nio.Keys.{allInputFiles, changedInputFiles, fileInputs, inputFileStamps}
import sbtjsbundler.*


object JSBundlerPlugin extends AutoPlugin {

  override def trigger = allRequirements
  override def requires = ScalaJSPlugin

  object autoImport {

    // PUBLIC SETTINGS

    val bundlerImplementation = settingKey[JSBundler]("JS bundler implementation")

    val bundlerNpmManager = settingKey[NpmExecutor]("NPM package manager to be used for installing and running npm packages")

    val bundlerManagedSources = settingKey[Seq[File]]("Non-Scala sources to be bundled with Scala.js outputs")

    val bundlerConfigSources = settingKey[Seq[File]]("Configuration sources for the selected JS bundler")

    val bundlerOutputDirectory = settingKey[File]("Where to persist JS bundles")

    val bundlerServerScriptDirectory = settingKey[File]("Where to output the bundler's dev server script")

    val bundlerDevServerScriptName = settingKey[String]("Name to give the bundler's dev server script")

    val bundlerPreviewScriptName = settingKey[String]("Name to give the bundler's preview script")


    // PUBLIC TASKS

    val prepareBundle = taskKey[Unit]("Prepare code and configuration for bundling")

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

  import autoImport.*

  private def scopedBundler(
    config: Configuration,
    jsScope: TaskKey[sbt.Attributed[Report]],
  ) = Def.task {
    val bundler = (config / jsScope / bundlerImplementation).value

    val inputScriptOpt = {
      if (config == Test) {
        val testOutputDir = (Test / jsScope / scalaJSLinkerOutputDirectory).value
        Some(ScopedJSBundler.InputSource(testOutputDir, file("main.js")))
      } else None
    }

    val stage = if (jsScope == fullLinkJS) Stage.FullOpt else Stage.FastOpt

    val log = streams.value.log

    bundler.scoped(
      (config / jsScope / bundlerConfigSources).value,
      (Compile / jsScope / scalaJSLinkerOutputDirectory).value,
      inputScriptOpt,
      (config / jsScope / bundlerManagedSources).value,
      (config / jsScope / bundlerOutputDirectory).value,
      (config / jsScope / bundlerBuildDirectory).value,
      (config / jsScope / bundlerNpmManager).value,
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
      config / jsScope / bundlerNpmManager := bundlerNpmManager.value,
      config / jsScope / bundlerManagedSources := bundlerManagedSources.value,
      config / jsScope / bundlerConfigSources := bundlerConfigSources.value,
      config / jsScope / bundlerImplementation := bundlerImplementation.value,

      config / jsScope / bundlerTargetDirectory := (config / jsScope / crossTarget).value / targetSubdir,
      config / jsScope / bundlerBuildDirectory := (config / jsScope / bundlerTargetDirectory).value / "build",
      config / jsScope / bundlerOutputDirectory := (config / jsScope / bundlerTargetDirectory).value / "dist",

      config / jsScope / prepareBundle / fileInputs ++= {
        (config / jsScope / bundlerManagedSources).value.flatMap { source =>
            val absSource = source.getAbsoluteFile
            Seq(
              Glob(absSource, RelativeGlob.**),
              Glob(absSource),
            )
        } ++ (config / jsScope / bundlerConfigSources).value.map { source =>
            Glob(source.getAbsoluteFile)
        } ++ Seq(
          Glob(
            (Compile / jsScope / scalaJSLinkerOutputDirectory).value.getAbsoluteFile,
            RelativeGlob.**,
          ),
        ) ++ {
          if (config == Test)
            Seq(Glob(
              (Test / jsScope / scalaJSLinkerOutputDirectory).value.getAbsoluteFile,
              RelativeGlob.**),
            )
          else Nil
        }
      },

      config / jsScope / prepareBundle := {
        val changes = (config / jsScope / prepareBundle / changedInputFiles).value
        val current = (config / jsScope / prepareBundle / allInputFiles).value
        val previous = Previous.runtimeInEnclosingTask(config / jsScope / prepareBundle / inputFileStamps).value
        val fileChanges = previous.map(changes).getOrElse(FileChanges.noPrevious(current))
        val bundler = scopedBundler(config, jsScope).value
        if (fileChanges.hasChanges) {
          bundler.prepareBuildContext match {
            case Left(message) =>
              throw new MessageOnlyException(s"Bundler failed to prepare build context: $message")
            case _ => {}
          }
        }
      },

      config / jsScope / prepareBundle :=
        (config / jsScope / prepareBundle).dependsOn(
          // Tests need test and compile outputs, Compile just needs compile
          {
            if (config == Test) Seq(Test / jsScope, Compile / jsScope)
            else Seq(Compile / jsScope)
          }*
        ).value,

      config / jsScope / bundle / fileInputs :=
        (config / jsScope / prepareBundle / fileInputs).value,

      config / jsScope / bundle := {
        val changes = (config / jsScope / bundle / changedInputFiles).value
        val current = (config / jsScope / bundle / allInputFiles).value
        val previous = Previous.runtimeInEnclosingTask(config / jsScope / bundle / inputFileStamps).value
        val fileChanges = previous.map(changes).getOrElse(FileChanges.noPrevious(current))
        val bundler = scopedBundler(config, jsScope).value
        if (fileChanges.hasChanges) {
          bundler.installAndBuild match {
            case Left(message) =>
              throw new MessageOnlyException(s"Bundler failed to build: $message")
            case Right(value) => value
          }
        }
      },

      config / jsScope / bundle :=
        (config / jsScope / bundle).dependsOn(config / jsScope / prepareBundle).value,
    )
  }

  private val allPerConfigSettings = (for {
    config <- List(Compile, Test)
    stage <- List(Stage.FastOpt, Stage.FullOpt)
    setting <- perConfigSettings(config, stage)
  } yield setting)

  override lazy val projectSettings = Seq(
    bundlerNpmManager := NpmExecutor.Default,
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
      bundler.startDevServer()
    },

    Compile / fastLinkJS / startDevServerProcess :=
      (Compile / fastLinkJS / startDevServerProcess).dependsOn(Compile / fastLinkJS / bundle).value,

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

    Compile / fastLinkJS / generateDevServerScript :=
      (Compile / fastLinkJS / generateDevServerScript).dependsOn(
        Compile / fastLinkJS / bundle,
      ).value,

    Compile / fullLinkJS / bundlerServerScriptDirectory :=
      baseDirectory.value,

    Compile / fullLinkJS / bundlerDevServerScriptName :=
      (Compile / fastLinkJS / bundlerDevServerScriptName).value + "-prod",

    Compile / fullLinkJS / generateDevServerScript := {
      val bundler = scopedBundler(Compile, fullLinkJS).value
      val outputDirectory = (Compile / fullLinkJS / bundlerServerScriptDirectory).value
      val scriptName = (Compile / fullLinkJS / bundlerDevServerScriptName).value
      bundler.generateDevServerScript(outputDirectory, scriptName)
    },

    Compile / fullLinkJS / generateDevServerScript :=
      (Compile / fullLinkJS / generateDevServerScript).dependsOn(
        Compile / fullLinkJS / bundle,
      ).value,

    Compile / fullLinkJS / bundlerPreviewScriptName := "start-preview",

    Compile / fullLinkJS / generatePreviewScript := {
      val bundler = scopedBundler(Compile, fullLinkJS).value
      val outputDirectory = (Compile / fullLinkJS / bundlerServerScriptDirectory).value
      val scriptName = (Compile / fullLinkJS / bundlerPreviewScriptName).value
      bundler.generatePreviewScript(outputDirectory, scriptName)
    },

    Compile / fullLinkJS / generatePreviewScript :=
      (Compile / fullLinkJS / generatePreviewScript).dependsOn(
        Compile / fullLinkJS / bundle,
      ).value,

    Compile / fastLinkJS / bundlerPreviewScriptName :=
      (Compile / fullLinkJS / bundlerPreviewScriptName).value + "-dev",

    Compile / fastLinkJS / generatePreviewScript := {
      val bundler = scopedBundler(Compile, fastLinkJS).value
      val outputDirectory = (Compile / fastLinkJS / bundlerServerScriptDirectory).value
      val scriptName = (Compile / fastLinkJS / bundlerDevServerScriptName).value
      bundler.generatePreviewScript(outputDirectory, scriptName)
    },

    Compile / fastLinkJS / generatePreviewScript :=
      (Compile / fastLinkJS / generatePreviewScript).dependsOn(
        Compile / fastLinkJS / bundle,
      ).value,

    generateDevServerScript := (Compile / fastLinkJS / generateDevServerScript).value,

    generatePreviewScript := (Compile / fullLinkJS / generatePreviewScript).value,

    Compile / jsEnvInput := {
      val bundler = scopedBundler(Compile, fastLinkJS).value
      bundler.outputScript.map { script =>
        Input.Script(script.toPath)
      } match {
        case Some(input) => Seq(input)
        case None => (Compile / jsEnvInput).value
      }
    },

    Test / jsEnvInput := {
      val bundler = scopedBundler(Test, fastLinkJS).value
      bundler.outputScript.map { script =>
        Input.Script(script.toPath)
      } match {
        case Some(input) => Seq(input)
        case None => (Test / jsEnvInput).value
      }
    },

    Test / test := (Test / test).dependsOn(
      Test / fastLinkJS / bundle
    ).value,
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
