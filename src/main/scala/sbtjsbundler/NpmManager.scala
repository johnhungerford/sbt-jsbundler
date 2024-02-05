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
	final case class Config(
		installExtraArgs: Seq[String] = Nil,
		installEnvironment: Map[String, String] = Map.empty[String, String],
	) {
		def withArgs(args: String*): Config = copy(installExtraArgs = args)
		def addArgs(args: String*): Config = copy(installExtraArgs = installExtraArgs ++ args)
		def withEnv(vars: (String, String)*): Config = copy(installEnvironment = vars.toMap)
		def addEnv(vars: (String, String)*): Config = copy(installEnvironment = installEnvironment ++ vars)
	}

	private[sbtjsbundler] def depString(tup: (String, String)): String = {
		s"${tup._1}@${tup._2}"
	}

	private[sbtjsbundler] def depsString(deps: Seq[(String, String)]): String = {
		deps.map(depString).mkString(" ")
	}

	val Default: NpmManager = new NpmNpmManager(NpmManager.Config())
}

final class NpmNpmManager(
	config: NpmManager.Config,
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
		val extraArgsString =  " " + config.installExtraArgs.mkString(" ")
		val existingCommand = baseCommand + extraArgsString
		val command = baseCommand + " --save " + NpmManager.depsString(deps.toSeq) + extraArgsString
		val devCommand = baseCommand + " --save-dev " + NpmManager.depsString(devDeps.toSeq) + extraArgsString

		val env = config.installEnvironment ++ environment

		println(existingCommand)
		if (deps.nonEmpty) println(command)
		if (devDeps.nonEmpty) println(devCommand)

		cwd.foreach(sbt.IO.createDirectory)
		val existingResult = Process(existingCommand, cwd, env.toSeq*).run().exitValue()
		val result = if (deps.isEmpty) 0 else Process(command, cwd, env.toSeq*).run().exitValue()
		val devResult = if (devDeps.isEmpty) 0 else Process(devCommand, cwd, env.toSeq*).run().exitValue()

		for {
			_ <- if (existingResult == 0) Right(()) else Left(s"Failed to install npm dependencies using npm. Exit code: $result")
			_ <- if (result == 0) Right(()) else Left(s"Failed to install npm dependencies using npm. Exit code: $result")
			_ <- if (devResult == 0) Right(()) else Left(s"Failed to install npm dev dependencies using npm. Exit code: $devResult")
		} yield ()
	}
}

final class YarnNpmManager(config: NpmManager.Config) extends NpmManager {
	def install(
		deps: Map[String, String],
		devDeps: Map[String, String],
		additionalOptions: Seq[String],
		environment: Map[String, String],
		cwd: Option[sbt.File],
	): Either[String, Unit] = {
		val additionalOptionsStr = additionalOptions.mkString(" ")
		val installCommand =
			s"yarn install $additionalOptionsStr"
		val addCommand =
			s"yarn add $additionalOptionsStr"
		val extraArgsString =  " " + config.installExtraArgs.mkString(" ")
		val existingCommand = installCommand + extraArgsString
		val command = addCommand + NpmManager.depsString(deps.toSeq) + extraArgsString
		val devCommand = addCommand + " --dev " + NpmManager.depsString(devDeps.toSeq) + extraArgsString

		val env = config.installEnvironment ++ environment

		println(existingCommand)
		if (deps.nonEmpty) println(command)
		if (devDeps.nonEmpty) println(devCommand)

		cwd.foreach(sbt.IO.createDirectory)
		val existingResult = Process(existingCommand, cwd, env.toSeq*).run().exitValue()
		val result = if (deps.isEmpty) 0 else Process(command, cwd, env.toSeq*).run().exitValue()
		val devResult = if (devDeps.isEmpty) 0 else Process(devCommand, cwd, env.toSeq*).run().exitValue()

		for {
			_ <- if (existingResult == 0) Right(()) else Left(s"Failed to install npm dependencies using yarn. Exit code: $result")
			_ <- if (result == 0) Right(()) else Left(s"Failed to install npm dependencies using yarn. Exit code: $result")
			_ <- if (devResult == 0) Right(()) else Left(s"Failed to install npm dev dependencies using yarn. Exit code: $devResult")
		} yield ()
	}
}

final class PnpmNpmManager(
	config: NpmManager.Config
) extends NpmManager {
	def install(
		deps: Map[String, String],
		devDeps: Map[String, String],
		additionalOptions: Seq[String],
		environment: Map[String, String],
		cwd: Option[sbt.File],
	): Either[String, Unit] = {
		val additionalOptionsStr = additionalOptions.mkString(" ")
		val installCommand =
			s"pnpm install $additionalOptionsStr"
		val addCommand =
			s"pnpm add $additionalOptionsStr"
		val extraArgsString =  " " + config.installExtraArgs.mkString(" ")
		val existingCommand = installCommand + extraArgsString
		val command = addCommand + "--save-prod" + NpmManager.depsString(deps.toSeq) + extraArgsString
		val devCommand = addCommand + " --save-dev " + NpmManager.depsString(devDeps.toSeq) + extraArgsString

		val env = config.installEnvironment ++ environment

		println(existingCommand)
		if (deps.nonEmpty) println(command)
		if (devDeps.nonEmpty) println(devCommand)

		cwd.foreach(sbt.IO.createDirectory)
		val existingResult = Process(existingCommand, cwd, env.toSeq*).run().exitValue()
		val result = if (deps.isEmpty) 0 else Process(command, cwd, env.toSeq*).run().exitValue()
		val devResult = if (devDeps.isEmpty) 0 else Process(devCommand, cwd, env.toSeq*).run().exitValue()

		for {
			_ <- if (existingResult == 0) Right(()) else Left(s"Failed to install npm dependencies using pnpm. Exit code: $result")
			_ <- if (result == 0) Right(()) else Left(s"Failed to install npm dependencies using pnpm. Exit code: $result")
			_ <- if (devResult == 0) Right(()) else Left(s"Failed to install npm dev dependencies using pnpm. Exit code: $devResult")
		} yield ()
	}
}
