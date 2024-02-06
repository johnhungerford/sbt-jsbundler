# Example: Complex multi-language frontend project

This project uses sbt-jsbundler to build a complex multi-language frontend 
project.

The project is a calculator SPA built using React. Most of it is written in Scala.js,
but the button and display components are JavaScript, as is the top-level script that
mounts the top-level react component. The display component also imports a utility 
exported from Scala.js. It thus demonstrates bidirectional interoperability between
Scala.js and JavaScript.

## Build structure

```
├── README.md
├── build.sbt
├── project
├── test
├── package.json
├── package-lock.json
├── jsbundler
│    ├── package.json
│    ├── package-lock.json
│    ├── index.html
│    ├── main.jsx
│    └── public
│         └── logo.png
└── src
     ├── main
     │     ├── javascript
     │     ├── scala
     │     └── styles
     └── test
           └── scala
```

Note that we keep all of our configuration and entry point assets in a root-level directory 
`jsbundler`. While these are usually at the project root in a typical JS project, keeping 
them in a separate directory makes it easier to import them into `sbt-jsbundler`. Note also 
the `javascript` and `styles` source directories alongside our usual `scala` source directory.

Note that the `package.json` and `package-lock.json` in the root directory are only for 
providing the dependencies required by the JS environment used for testing. There is a separate
`package.json` and `package-lock.json` in the `jsbundler` directory which define the 
dependencies for our Scala.js/JS project.

## Build settings

The build settings specific to sbt-jsbundler are:

```sbt
enablePlugins(ScalaJSPlugin, JSBundlerPlugin)

bundlerImplementation := sbtjsbundler.vite.ViteJSBundler()
    .addDevEnvVariable("NODE_ENV" -> "development")

bundlerManagedSources ++= Seq(
	file("src/main/javascript"),
	file("src/main/styles"),
	file("jsbundler")
)
```

`bundlerImplementation` selects which bundler tool we want to use to build our project
(currently only `vite` is supported). We include an additional configuration to 
update `NODE_ENV` during development builds. This will ensure that our tests use 
development builds of react, which allow certain testing methods.

`bundlerManagedSources` selects the non-Scala source directories to include. The 
contents of these will be injected into the build directory (excluding the root directory) 
for each build.

## Dependency resolution

### NPM dependencies

In both Scala.js and javascript files, npm dependencies are imported in the 
usual way, e.g.,

```javascript
// From src/main/javascript/component/Button.jsx
import React, { Component } from 'react';
import PropTypes from 'prop-types';
...
```

All of these dependencies must be installed in the project root prior to building.

### Local dependencies

To import local dependencies into your Scala.js source code, construct your imports 
using absolute paths starting from directories included in `bundlerManagedSources`:

```scala
// From src/main/scala/example/components/Display.scala

// Imports from src/main/styles/buttonPanel.css
@js.native
@JSImport("/buttonPanel.css", JSImport.Namespace)
object ButtonPanelCss extends js.Object

// Imports from src/main/javascript/component/Button.jsx
@js.native
@JSImport("/component/Button.jsx", JSImport.Default)
object ButtonRaw extends js.Object
```

### Scala.js dependencies

The above example shows how a Scala.js file can import a non-Scala.js, but what 
about importing in the other direction? That is, how do import from Scala.js into 
a JavaScript or TypeScript file? 

This can be accomplished by prefixing imports with `scalajs:`. sbt-jsbundler will resolve
this as the output directory of `fullLinkJS` or `fastLinkJS` depending on which of these 
you use when you build your bundle. When there is nothing after the colon, it 
is equivalent to `scalajs:main.js` (`main.js` by default being the main output of the Scala.js
linker).

For instance, the entrypoint script in this project pulls the top level react app from 
our Scala.js source as follows:

```javascript
// From main.jsx (project root)
import React from 'react';
import { createRoot } from 'react-dom/client';

import App from 'scalajs:';

const root = createRoot(document.getElementById('app'))
root.render(<App />, );
```

The above import will work because our Scala.js code includes a top-level default 
export:

```scala
// From src/main/scala/example/App.scala

	@JSExportTopLevel("default")
	val rawApp =
		component
		  .cmapCtorProps[Unit](identity) // Change props from JS to Scala
		  .toJsComponent // Create a new, real JS component
		  .raw // Leave the nice Scala wrappers behind and obtain the underlying JS value
```

It is also possible to separate Scala.js exports into separate modules by specifying a
`moduleId`. For instance, the following exports a utility function to a separate module:

```scala
// From src/main/scala/example/util/Utils.scala
	@JSExportTopLevel("formatNumberString", "utils")
	def formatNumberString(numberString: String): String = numberString match {
		case Value.NonDecimal() => addCommasNonDecimal(numberString)
		case Value.Decimal(nonDecimal, decimal) =>
			val nonDecimalWithCommas = addCommasNonDecimal(nonDecimal)
			s"$nonDecimalWithCommas.$decimal"
		case "" => "0"
		case _ => throw new RuntimeException(s"Invalid number string: $numberString")
	}
```

Since we specify a `moduleId` of "utils" in the second parameter of
`JSExportTopLevel`, the Scala.js linker will create a second file called `utils.js` 
that exports the annotated function. We can then import it as follows:

```javascript
// From src/main/javascript/component/Display.jsx
import { formatNumberString } from 'scalajs:utils.js'
```

## Building and testing

Prior to building, we need to make sure all dependencies are in place. Not only does 
this include the dependencies we need for our project, but sbt-jsbundler has additional 
dev dependencies. All the dependencies are included in `jsbundler/package.json`

To build the project, run

```shell
sbt bundle
```

or

```shell
sbt fastLinkJS / bundle
```

This will generate a bundle which you can retrieve from:

```shell
target/scala-3.3.1/jsbundler-[mode]/bundle
```

where `mode` is either `opt` or `fastopt`.

### Testing

To run tests, simply run the usual task:

```shell
sbt test
```

This will bundle the compiled test code with all dependencies, so feel free to
include any dependencies in your unit tests! It will also include your compiled 
non-test Scala.js outputs as well, so any JS sources that import from your Scala.js
project that are included in your tests can be used in tests as well. For instance 
in `src/test/component/DisplayComponentTest` we test a React component that imports 
`src/main/javascript/component/Display.jsx`, which in turn imports a utility 
function from Scala.js.

Note that since we are testing react components, we need to be able to render them in
a dom. Accordingly, we have included the following setting in `build.sbt`:

```sbt
Test / jsEnv :=
	new JSDOMNodeJSEnv(
		JSDOMNodeJSEnv.Config()
			.withArgs(List("-r", "source-map-support/register"))
	)
```

We include the `-r source-map-support/register` arguments to ensure that source maps are
used when generating stack traces. This configuration will require that both `jsdom` and 
`source-map-support` packages are accessible to the Scala.js test-runner context. It is 
not enough to have them in our `jsbundler/package.json` as the Scala.js plugin doesn't 
know to look there. We have therefore included a separate `package.json` at our project 
root with just these two packages. A task is included in `build.sbt` to install them on 
startup.

### Development server

You can run a dev server by running the following:

```shell
sbt "~startDevServer" stopDevServer
```

This will start a dev server in the background and update with any changes to either
your Scala.js code or anything within `bundlerManagedSources`. After you break out of 
the first command (~startDevServer) by pressing `Enter`, `stopDevServer` will then 
close out the background task.

Be warned that if sbt closes unexpectedly, the dev server will continue to run in the 
background. This will happen whenever you run `startDevServer` without running 
`stopDevServer` before exiting sbt.

Alternatively, you can simply run the dev server outside of sbt by generating a script:

```shell
sbt generateDevServerScript
```

and then running it from the project root:

```shell
./start-dev-server.sh
```

To hot-reload your Scala.js sources, execute the following sbt task in a separate
shell:

```shell
sbt "~fastLinkJS/prepareBundleSources"
```
or, if it's likely that you'll make incremental updates to the dependencies in `package.json`
in addition to updating your Scala.js code:
```shell
sbt "~fastLinkJS/installNpmDependencies"
```

### Preview server

You can start a preview server, which will simply serve your production bundle, in a very 
similar way to the dev server:

```shell
sbt "~startPreview" stopPreview
```

Or

```shell
sbt generatePreviewScript
start-preview.sh
```

