package sbtjsbundler.vite

import sbt._

object ViteScriptGen {
	def generateDevServerScript(
		config: sbt.File,
		cwd: sbt.File,
	): Either[String, String] = Right {
		s"""#!/bin/sh
		   |set -m
		   |original_dir=$$(pwd)
		   |cd ${cwd.getAbsolutePath}
		   |node ./node_modules/vite/bin/vite.js -c ${config.getAbsolutePath} &
		   |cd $$original_dir
		   |fg %1
		   |""".stripMargin
	}

	def generatePreviewScript(
		config: sbt.File,
		cwd: sbt.File,
	): Either[String, String] = {
		config
		  .relativeTo(cwd)
		  .toRight("Could not generate a valid preview script: configuration path cannot be relative to build directory")
		  .map { configFile =>
			  s"""#!/bin/sh
				 |set -m
				 |original_dir=$$(pwd)
				 |cd ${cwd.getAbsolutePath}
				 |node ./node_modules/vite/bin/vite.js preview -c ${configFile} &
				 |cd $$original_dir
				 |fg %1
				 |""".stripMargin
		  }

	}
}
