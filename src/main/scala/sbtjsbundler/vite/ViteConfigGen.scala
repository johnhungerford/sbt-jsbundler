package sbtjsbundler.vite

import scala.util.{Failure, Success, Try}

/**
 * Generate a vite configuration file for bundling test scripts. Currently does
 * simple string concatenation to generate a JS file. As complexity increases, this
 * will have to be replaced with some proper codegen approach.
 */
object ViteConfigGen {

	/**
	 * Generates a vite configuration file (in the form of a string), which is itself
	 * and executable JavaScript file that defines and exports a configuration object.
	 *
	 * To customize the configuration, one or more `sourcePath`s can pe provided, which
	 * must point to other JavaScript files. These are expected to provide a default
	 * export of either a vite `UserConfig` object, or a function that consumes a
	 * vite `ConfigEnv` object and returns a `UserConfig`. They should not call `defineConfig`
	 * as this is done in the merged script.
	 *
	 * The generated script will import all of the sources, generate and merge their
	 * configurations, and then ensure that the final configuration is pointing to the correct
	 * locations for consuming Scala.js JS outputs and generating bundles. These values cannot
	 * be manually overridden.
	 *
	 * @param sourcePaths paths of custom vite configuration scripts. These must export a
	 *                    configuration or a function from `UserConfig` to a configuration.
	 *                    They will be merged or overwrite each other from "left to right".
	 *                    In other words, subsequent files will take precedence.
	 * @param rootDirPath the base directory that vite will use to resolve inputs
	 * @param inputPath   the path, relative to rootDirPath, of the JS entrypoint to be bundled
	 * @param outDirPath  the absolute path (or relative to the CWD when executing vite) where
	 *                    vite will output bundled artifacts
	 * @param plugins     plugins to add to config. Maps the plugin entry to any import statements
	 *                    required to bring the plugin into scope.
	 *                    E.g., Map("react()" -> "import react from '@vitejs/plugin-react'")
	 * @param development Generate a config for development (i.e., for testing or dev server)
	 * @return Left if any of the four inputs are invalid, right with the generated JS string if not
	 */
	def generate(
		sourcePaths: List[String],
		rootDirPath: String,
		inputPath: Option[String],
		outDirPath: String,
		otherImports: List[String] = Nil,
		plugins: List[String] = Nil,
		rollupPlugins: List[String] = Nil,
		development: Boolean = true,
	): Either[ValidationError, String] = {
		for {
			_ <- validatePathForJSInjection(rootDirPath).toLeft(())
			_ <- inputPath.fold[Either[ValidationError, Unit]](Right(()))(
				v => validatePathForJSInjection(v).toLeft(())
			)
			_ <- validatePathForJSInjection(outDirPath).toLeft(())
			_ <- sourcePaths.foldLeft[Either[ValidationError, Unit]](Right(())) {
				(currentEither, nextPath) =>
					currentEither.flatMap(_ => validatePathForJSInjection(nextPath).toLeft(()))
			}
		} yield combineAll(
			otherImports ++ generateImportStatements(sourcePaths),
			generatePluginsValue(plugins),
			generatePluginsValue(rollupPlugins),
			generateBuildConfigStatements(sourcePaths.length),
			rootDirPath,
			inputPath,
			outDirPath,
			development,
		)
	}

	final case class ValidationError(message: String)

	/**
	 * Ensures that paths (currently, the only kind of input for code gen) are both
	 * valid paths and do contain obvious tokens for injecting scripts (e.g., newlines,
	 * line-delimiting semicolons, or quotes).
	 *
	 * @param path a path that will injected into a JS script
	 * @return
	 */
	private def validatePathForJSInjection(path: String): Option[ValidationError] =
		Try(java.nio.file.Path.of(path)) match {
			case Failure(exception) =>
				Some(ValidationError(
					s"Invalid configuration source path: ${path}\n${exception}",
				))
			case Success(_) if path.contains(";") =>
				Some(ValidationError(
					"Invalid configuration source path: ${path}\nPath contains ';'",
				))
			case Success(_) if path.contains("\n") =>
				Some(ValidationError(
					"Invalid configuration source path: ${path}\nPath contains new line character",
				))
			case Success(_) if path.contains("'") || path.contains("\"") =>
				Some(ValidationError(
					"Invalid configuration source path: ${path}\nPath contains single or double quote",
				))
			case _ => None
		}

	private val configVariableName = "config"
	private val minimumConfigVariableName = "minimalConfig"
	private val envVariableName = "env"

	private val developmentDefaultConfig =
		"""{
		  |  mode: 'development',
		  |  build: {
		  |    cssCodeSplit: false,
		  |    minify: false,
		  |    chunkSizeWarningLimit: 100000,
		  |    reportCompressedSize: false,
		  |    sourcemap: true,
		  |    rollupOptions: {
		  |      treeshake: false,
		  |    },
		  |  },
		  |}""".stripMargin

	private val productionDefaultConfig =
		"""{
		  |  mode: 'production',
		  |}""".stripMargin

	private def combineAll(
		importStatements: List[String],
		pluginsValue: String,
		rollupPluginsValue: String,
		buildConfigStatements: List[String],
		rootDirPath: String,
		inputPath: Option[String],
		outputDirPath: String,
		development: Boolean,
	): String = {
		val importsString = importStatements.mkString("\n")
		val buildConfigString = buildConfigStatements.mkString("\n  ")
		val defaultConfigString =
			if (development) developmentDefaultConfig
			else productionDefaultConfig

		s"""$importsString
		   |
		   |const $configVariableName = $defaultConfigString
		   |
		   |const $minimumConfigVariableName = {
		   |  root: "$rootDirPath",
		   |  plugins: $pluginsValue,
		   |  build: {
		   |  	outDir: "$outputDirPath",
		   |    rollupOptions: {${inputPath.fold("")(v => s"""\n      input: "${v}",""")}
		   |      plugins: $rollupPluginsValue,
		   |      output: {
		   |      	entryFileNames: `[name].js`,
		   |      },
		   |    },
		   |  },
		   |}
		   |
		   |export default defineConfig(($envVariableName) => {
		   |  $buildConfigString
		   |
		   |  return $configVariableName;
		   |})
		   |""".stripMargin
	}

	private def configName(i: Int): String = s"config_$i"
	private def appliedConfigName(i: Int): String = configName(i) + "_applied"

	private def generateImportStatements(sourcePaths: List[String]): List[String] = {
		sourcePaths.zipWithIndex.map {
			case (path, i) => s"""import ${configName(i)} from './$path';"""
		}
	}

	private def generatePluginsValue(pluginExpressions: List[String]): String = {
		pluginExpressions
		  .map(_.trim.stripSuffix(",").trim)
		  .mkString("[", ", ", "]")
	}

	private def generateBuildConfigStatements(numConfigs: Int): List[String] = {
		val applyConfigStatements = (0 until numConfigs).toList
														.map {i =>
															val conf = configName(i)
															s"""const ${appliedConfigName(i)} = $conf instanceof Function ? $conf($envVariableName) : $conf;"""
														}

		val configArgs = ((0 until numConfigs).map(appliedConfigName) :+ minimumConfigVariableName).mkString(", ")

		applyConfigStatements ++ List(
			"""const customizer = (objValue, srcValue) => _.isArray(objValue) && _.isArray(srcValue) ? objValue.concat(srcValue) : undefined;""",
			s"""_.mergeWith($configVariableName, $configArgs, customizer);""",
		)
	}
}
