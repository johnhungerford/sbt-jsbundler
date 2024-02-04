package sbtjsbundler

import scala.sys.process._

trait NpmExecutor {
	def install(
		deps: Map[String, String],
		devDeps: Map[String, String],
		additionalOptions: Seq[String],
		environment: Map[String, String],
		cwd: Option[sbt.File],
	): Either[String, Unit]

	def runProcess(
		pckg: String,
		module: String,
		additionalOptions: Seq[String],
		environment: Map[String, String],
		cwd: Option[sbt.File],
	): Process

	def run(
		pckg: String,
		module: String,
		additionalOptions: Seq[String],
		environment: Map[String, String],
		cwd: Option[sbt.File],
	): Either[String, Unit]
}

object NpmExecutor {
	private[sbtjsbundler] def depString(tup: (String, String)): String = {
		s"${tup._1}@${tup._2}"
	}

	private[sbtjsbundler] def depsString(deps: Seq[(String, String)]): String = {
		deps.map(depString).mkString(" ")
	}

	val Default: NpmExecutor = NpmNpmExecutor()
}

final case class NpmNpmExecutor(
	extraArgs: Seq[String] = Nil,
	environment: Map[String, String] = Map.empty[String, String],
) extends NpmExecutor {
	def install(
		deps: Map[String, String],
		devDeps: Map[String, String],
		additionalOptions: Seq[String],
		environment: Map[String, String],
		cwd: Option[sbt.File],
	): Either[String, Unit] = {
		val additionalOptionsStr = additionalOptions.mkString(" ")
		val baseCommand =
			s"npm install $additionalOptionsStr"
		val extraArgsString =  " " + extraArgs.mkString(" ")
		val existingCommand = baseCommand + extraArgsString
		val command = baseCommand + " --save " + NpmExecutor.depsString(deps.toSeq) + extraArgsString
		val devCommand = baseCommand + " --save-dev " + NpmExecutor.depsString(devDeps.toSeq) + extraArgsString

		println(existingCommand)
		if (deps.nonEmpty) println(command)
		if (devDeps.nonEmpty) println(devCommand)

		cwd.foreach(sbt.IO.createDirectory)
		val existingResult = Process(existingCommand, cwd, environment.toSeq*).run().exitValue()
		val result = if (deps.isEmpty) 0 else Process(command, cwd, environment.toSeq*).run().exitValue()
		val devResult = if (devDeps.isEmpty) 0 else Process(devCommand, cwd, environment.toSeq*).run().exitValue()

		for {
			_ <- if (existingResult == 0) Right(()) else Left(s"Failed to install npm dependencies using npm. Exit code: $result")
			_ <- if (result == 0) Right(()) else Left(s"Failed to install npm dependencies using npm. Exit code: $result")
			_ <- if (devResult == 0) Right(()) else Left(s"Failed to install npm dev dependencies using npm. Exit code: $devResult")
		} yield ()
	}

	def runProcess(
		pckg: String,
		module: String,
		arguments: Seq[String],
		environment: Map[String, String],
		cwd: Option[sbt.File],
	): Process = {
		val baseCommand = s"npx --no -p $pckg $module"

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

object YarnNpmExecutor extends NpmExecutor {
	def install(
		deps: Map[String, String],
		devDeps: Map[String, String],
		additionalOptions: Seq[String],
		environment: Map[String, String],
		cwd: Option[sbt.File],
	): Either[String, Unit] = {
		val additionalOptionsStr = additionalOptions.mkString(" ")
		val baseCommand = s"yarn add $additionalOptionsStr"
		val command = baseCommand + " " + NpmExecutor.depsString(deps.toSeq)
		val devCommand = baseCommand + " --dev " + NpmExecutor.depsString(devDeps.toSeq)

		cwd.foreach(sbt.IO.createDirectory)
		val existingResult = Process(baseCommand, cwd, environment.toSeq*).run().exitValue()
		val result = Process(command, cwd, environment.toSeq*).run().exitValue()
		val devResult = Process(devCommand, cwd, environment.toSeq*).run().exitValue()

		for {
			_ <- if (existingResult == 0) Right(()) else Left(s"Failed to install npm dependencies using npm. Exit code: $result")
			_ <- if (result == 0) Right(()) else Left(s"Failed to install npm dependencies using npm. Exit code: $result")
			_ <- if (devResult == 0) Right(()) else Left(s"Failed to install npm dev dependencies using npm. Exit code: $devResult")
		} yield ()
	}

	def runProcess(
		pckg: String,
		module: String,
		arguments: Seq[String],
		environment: Map[String, String],
		cwd: Option[sbt.File],
	): Process = {
		val baseCommand = s"npx --no -p $pckg $module"

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

object PnpmNpmExecutor extends NpmExecutor {
	def install(
		deps: Map[String, String],
		devDeps: Map[String, String],
		additionalOptions: Seq[String],
		environment: Map[String, String],
		cwd: Option[sbt.File],
	): Either[String, Unit] = {
		val additionalOptionsStr = additionalOptions.mkString(" ")
		val baseCommand =
			s"pnpm add $additionalOptionsStr"
		val command = baseCommand + " " + NpmExecutor.depsString(deps.toSeq)
		val devCommand = baseCommand + " --save-dev " + NpmExecutor.depsString(devDeps.toSeq)

		cwd.foreach(sbt.IO.createDirectory)
		val existingResult = Process(baseCommand, cwd, environment.toSeq*).run().exitValue()
		val result = Process(command, cwd, environment.toSeq*).run().exitValue()
		val devResult = Process(devCommand, cwd, environment.toSeq*).run().exitValue()

		for {
			_ <- if (existingResult == 0) Right(()) else Left(s"Failed to install npm dependencies using npm. Exit code: $result")
			_ <- if (result == 0) Right(()) else Left(s"Failed to install npm dependencies using npm. Exit code: $result")
			_ <- if (devResult == 0) Right(()) else Left(s"Failed to install npm dev dependencies using npm. Exit code: $devResult")
		} yield ()
	}

	def runProcess(
		pckg: String,
		module: String,
		arguments: Seq[String],
		environment: Map[String, String],
		cwd: Option[sbt.File],
	): Process = {
		val baseCommand = s"pnpx exec $module"

		val command = s"$baseCommand ${arguments.mkString(" ")}"

		cwd.foreach(sbt.IO.createDirectory)
		Process(command, cwd, environment.toSeq*).run()
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
		else Left(s"Failed to run $module in npm package $pckg using pnpx. Exit code: $result")
	}
}
