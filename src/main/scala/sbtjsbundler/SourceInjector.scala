package sbtjsbundler

import scala.util.Try
import sbt._

object SourceInjector {
	private def injectFile(file: sbt.File, target: sbt.File): Either[String, Unit] = Try {
		if (file.exists()) {
			if (!target.exists()) sbt.IO.createDirectory(target)
			if (!target.isDirectory) throw new IllegalArgumentException(s"destination is not directory: $target")
			if (file.isDirectory) {
				sbt.IO.copyDirectory(file, target)
			} else {
				sbt.IO.copyFile(file, target / file.getName)
			}
		} else println(s"WARNING: source file does not exist: $file")
	}.toEither.left.map(v => { v.printStackTrace(); v.getMessage })

	def inject(paths: Seq[sbt.File], destination: sbt.File): Either[String, Unit] = {
		paths.foldLeft[Either[String, Unit]](Right(())) { (lastAttempt, nextPath) =>
			lastAttempt.flatMap(_ => injectFile(nextPath, destination))
		}
	}

	def sourceGlobs(paths: Seq[sbt.File]): Seq[sbt.Glob] = {
		import sbt.Glob.stringToGlob
		paths.map(path => stringToGlob {
			if (path.isDirectory) path.toPath.toString.stripSuffix("/") + "/**"
			else path.toPath.toString
		})
	}
}