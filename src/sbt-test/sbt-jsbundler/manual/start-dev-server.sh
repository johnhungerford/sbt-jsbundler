#!/bin/sh
set -m
original_dir=$(pwd)
cd /Users/john.hungerford/projects/personal/sbt-jsbundler/src/sbt-test/sbt-jsbundler/manual/target/scala-3.3.1/jsbundler-fastopt/build
node ./node_modules/vite/bin/vite.js -c /Users/john.hungerford/projects/personal/sbt-jsbundler/src/sbt-test/sbt-jsbundler/manual/target/scala-3.3.1/jsbundler-fastopt/build/__vite/vite.config-generated.js &
cd $original_dir
fg %1
