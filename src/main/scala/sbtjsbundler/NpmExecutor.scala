package sbtjsbundler

import scala.sys.process.Process

object NpmExecutor {
	def runProcess(
		pckg: String,
		module: String,
		arguments: Seq[String],
		environment: Map[String, String],
		cwd: Option[sbt.File],
	): Process = {
		val baseCommand = s"node node_modules/$pckg/bin/$module.js"

		val command = s"$baseCommand ${arguments.mkString(" ")}"

		cwd.foreach(sbt.IO.createDirectory)
		Process(command, cwd, environment.toSeq *).run()
	}

	def run(
		pckg: String,
		module: String,
		arguments: Seq[String],
		environment: Map[String, String],
		cwd: Option[sbt.File],
	): Either[String, Unit] = {
		val result = runProcess(pckg, module, arguments, environment, cwd).exitValue()

		if (result == 0) Right(())
		else Left(s"Failed to run $module in npm package $pckg using npx. Exit code: $result")
	}
}
