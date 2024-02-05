# sbt-jsbundler

An extensible sbt plugin for bundling Scala.js with non-Scala dependencies. Currently 
supports [vite](https://vitejs.dev/).

Informed largely by https://github.com/ptrdom/scalajs-vite

## Usage

This plugin requires sbt 1.0.0+.

To use sbt-vite in your project, add the following line to `projects/plugins.sbt`:

```sbt
addSbtPlugin("io.github.johnhungerford" % "sbt-jsbundler" % "0.0.3")
```

In `build.sbt`, include `JSBundlerPlugin` in `.enablePlugins(...)` in any Scala.js project
that needs to be bundled with JavaScript dependencies. Add the following setting to your 
jsbundler project as well:

```sbt
bundlerManagedSources ++= Seq(
  ...
)
```

where `Seq(...)` contains a list of directories and files that should be included in your 
bundle. This should include your `package.json`/`package-lock.json` as well as your any 
assets and non-Scala source you want to include.

### Building

Add the following files on of your `bundlerManagedSources` directories in your project or
sub-project:

`index.html`
```html
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>sbt-vite</title>
  </head>
  <body>
    <script type="module" src="/main.js"></script>
  </body>
</html>
```

If your Scala.js project is runnable (i.e., if it has a `def main` entrypoint and
`scalaJSUseMainModuleInitializer := true` in build.sbt), you can simply import the
application to be run as follows:

`main.js`:
```javascript
// 'scalajs:' will be resolved by vite to the output of the Scala.js linker
import 'scalajs:'
```

Otherwise you can import any exported objects from your Scala.js project as
follows:

`main.js`:
```javascript
import { someLib, otherLib } from 'scalajs:';

...

someLib.doSomething();
otherLib.doSomethingElse();

...
```

Once you have your html and js entrypoints in place, you can run the following to
generate a web bundle:

```shell
sbt bundle
```

This will compile your project, generate an appropriate vite configuration, and run
vite on all artifacts. By default, the bundle will persisted at
`[project-directory]/target/scala-[x.x.x]/jsbundler-opt/dist`. Use `sbt fastLinkJS/bundle`
to run build in development mode and skip optimizations.

To launch a development server, you can run:

```shell
sbt "~startDevServer" stopDevServer
```

or generate a script to launch a dev server without having to use the sbt console:

```shell
sbt generateDevServerScript
```

This will output a shell script `start-dev-server.sh` at your project root. It's
recommended to use this script to launch the dev server rather than sbt so that you
can use your sbt console to run `~prepareBundlerSources`. This will update your build
as you edit your Scala.js files so that vite can reload the page.

### Testing

Run tests using the usual command:

```shell
sbt test
```

This will use vite to bundle the linked JavaScript test executable with any dependencies
prior to running it.

## Dependency management

This plugin would not be of much use if it did not resolve dependencies properly. One of 
the advantages of sbt-jsbundler is that it provides a consistent pattern for resolving 
imports in both your Scala.js and your JS/TS code.

#### NPM dependencies

As long as `package.json` is included in your `bundlerManagedSources`, jsbundler will 
be able to resolve any usual npm package imports, both in your Scala.js and JS/TS code.

#### Other non-Scala.js sources

In addition to bundling npm modules, sbt-jsbundler will bundle Scala.js outputs with
local imports, such as `JavaScript`, `TypeScript`, `css`, `less`, and others.

Source files and directories should be declared for inclusion using the 
`bundlerManagedSources` setting:

```sbt
bundlerManagedSources := Seq(
    file("modules/frontend-project/src/main/typescript"),
	file("modules/frontend-project/src/main/styles"),
	file("modules/frontend-project/src/main/entrypoint"),
	file("common/typescript"),
	file("common/styles"),
)
```

In the above scenario, some TypeScript and CSS sources are included from sub-project source 
directories, while others are included from a common source directory at the project root. 

For any declared source that points to a directory, sbt-jsbundler will copy all the files
and directories within it to the build directory prior to running `vite`. Any declared
source that is a file will be copied directly to the build directory.

Accordingly, any sources declared in your build can be imported as expected:

```scala
// This will import either from [project]/src/main/typescript/someDir/someTypeScriptModule.ts
// or from common/typescript/someDir/someTypeScriptModule.ts
@js.native
@JSImport("/someDir/someTypeScriptModule", JSImport.Default)
object TypeScriptImport extends js.Object

// This will import either from [project]/src/main/styles/someStyle.css or from
// common/styles/someStyle.css
@js.native
@JSImport("/someStyle.css?inline", JSImport.Namespace)
object CssImport extends js.Object
```

These imports will work correctly in JS and TS sources as well:

```javascript
import someModule from '/someDir/someTypeScriptModule';
import '/someStyle.css';
```

#### Importing Scala.js outputs

To import Scala.js artifacts into JS/TS sources, simply prefix the imported path with `scalajs:`.

For example, if you Scala.js codebase includes the following export:

```scala
JSExportTopLevel("myExportedFunction", "myModule")
def myExportedFunction(i: Int, j: Int): Int = ???
```

You would import it in a JavaScript file as follows:

```javascript
import { myExportedFunction } from 'scalajs:myModule.js';
```

Note that if the `moduleId` "myModule" is left out of `JSExportTopLevel()`, it will be 
exported from a `main.js` script. Imports from `main.js` do not need to be stated explicitly,
so you could just import it as:

```javascript
import { myExportedFuction } from 'scalajs:';
```

Note the colon is still required to for the plugin to resolve it correctly.

### Build command customization

To customize the commands used to execute the various `vite` tasks supported by sbt-jsbundler,
you can configure the bundler implementation:

```sbt
bundlerImplementation := sbtjsbundler.vite.ViteJSBundler()
    .addDevExtraArg("--mode=development")
    .addDevEnvVariable("NODE_ENV" -> "development") // This one can be very hand
```

You can similarly add arguments and environment variables to the npm install commands:

```sbt
bundlerNpmManager := NpmNpmExecutor(extraArgs = List("--legacy-peer-deps"))
```

### Vite config overrides

The vite implementation of sbt-jsbundler generates configuration scripts with reasonable
defaults for full builds (i.e., `bundle`, which is an alias for `Compile / fullLinkJS/ bundle`),
development builds (i.e., `fastLinkJS / bundler`), and test builds (i.e., 
`Test / fastLinkJS / bundle`, which prepares a bundle to be executed by `Test / test`).

To override these defaults, you can use the setting `bundlerConfigSources` to provide
one or more configuration scripts that will be merged with the defaults, allowing
you to override various settings. Note that the following configuration properties
cannot be overridden:
1. `root`
2. `build.outDir`
3. `build.rollupOptions.input` (for tests only)

`bundlerConfigSources` must specify valid javascript files that provide a default export
of one of the following two forms:
1. a simple [vite configuration object](https://github.com/vitejs/vite/blob/997a6951450640fed8cf19e58dce0d7a01b92392/packages/vite/src/node/config.ts#L127)
2. a function that consumes a [vite environment configuration](https://github.com/vitejs/vite/blob/997a6951450640fed8cf19e58dce0d7a01b92392/packages/vite/src/node/config.ts#L78)
   and returns a [vite configuration](https://github.com/vitejs/vite/blob/997a6951450640fed8cf19e58dce0d7a01b92392/packages/vite/src/node/config.ts#L127).

Note that neither of these should be wrapped in `defineConfig`, as this will be called
after merging imported overrides.

Note also that the `viteConfigSources` will be merged in order, so later sources in the
`Seq` will have precedence over prior sources.

`bundlerConfigSources` can be scoped to both `Compile`/`Test` and `fullLinkJS`/`fastLinkJS` to
provide different customizations for different build types.

#### Example

The following vite config source provides overrides to support JSX (React),
bundle source maps (disabled by default except in tests), and break out several
library dependencies into separate chunks:

`build.sbt`:
```sbt
bundlerConfigSources += file("vite.config-build.js")
```

'vite.config-build.js':
```javascript
import react from '@vitejs/plugin-react';

import sourcemaps from 'rollup-plugin-sourcemaps';

export default (env)=> ({
  // Array properties will concat on merge, so this will be added
  // to plugins, instead of overwriting
  plugins: [
    react(), 
  ],
  build: {
    sourcemap: true,
    rollupOptions: {
      plugins: [sourcemaps()],
      output: {
        strict: false,
        chunkFileNames: '[name]-[hash:10].js',
        manualChunks: {
          lodash: ['lodash'],
          react: ['react'],
          'react-dom': ['react-dom'],
          'react-router-dom': ['react-router-dom'],
        }
      }
    },
  },
});
```

## Examples

See [src/sbt-test/sbt-jsbundler](src/sbt-test/sbt-jsbundler) for an example project. It includes
a `README.md` with further documentation.
