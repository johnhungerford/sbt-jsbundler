package sbtjsbundler

import scala.sys.process._

trait NpmManager {
	def install(
		deps: Map[String, String],
		devDeps: Map[String, String],
		additionalOptions: Seq[String],
		environment: Map[String, String],
		cwd: Option[sbt.File],
	): Either[String, Unit]
}

object NpmManager {
	private[sbtjsbundler] def depString(tup: (String, String)): String = {
		s"${tup._1}@${tup._2}"
	}

	private[sbtjsbundler] def depsString(deps: Seq[(String, String)]): String = {
		deps.map(depString).mkString(" ")
	}

	val Default: NpmManager = NpmNpmManager()
}

final case class NpmNpmManager(
	extraArgs: Seq[String] = Nil,
	environment: Map[String, String] = Map.empty[String, String],
) extends NpmManager {
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
		val command = baseCommand + " --save " + NpmManager.depsString(deps.toSeq) + extraArgsString
		val devCommand = baseCommand + " --save-dev " + NpmManager.depsString(devDeps.toSeq) + extraArgsString

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
}

object YarnNpmManager extends NpmManager {
	def install(
		deps: Map[String, String],
		devDeps: Map[String, String],
		additionalOptions: Seq[String],
		environment: Map[String, String],
		cwd: Option[sbt.File],
	): Either[String, Unit] = {
		val additionalOptionsStr = additionalOptions.mkString(" ")
		val baseCommand = s"yarn add $additionalOptionsStr"
		val command = baseCommand + " " + NpmManager.depsString(deps.toSeq)
		val devCommand = baseCommand + " --dev " + NpmManager.depsString(devDeps.toSeq)

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
}

object PnpmNpmManager extends NpmManager {
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
		val command = baseCommand + " " + NpmManager.depsString(deps.toSeq)
		val devCommand = baseCommand + " --save-dev " + NpmManager.depsString(devDeps.toSeq)

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
}
