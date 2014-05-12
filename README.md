play-requirejs
=========

**/!\ Do not use. Prefer [sbt-web][sbt-web] and [sbt-rjs][sbt-rjs] if you are looking for the same functionality.**

The motivation of this project was to workaround the shortcomings of Play's (< 2.3) implementation of RequireJS, mainly that it didn't cacth JavaScript exceptions and only exposed a limited subset of [r.js build file][build.js] functionalities. Those issues have been solved (or are being solved) by [sbt-rjs][sbt-rjs] which is used by Play 2.3 and is likely to be maintained; this project isn't. 

Original Documentation
=========

[Require.js][require] optimizer plugin for [sbt][sbt].

## Sbt command

The plugin defines a single command named `rjs`.

    $ rjs
    [info] See https://github.com/jrburke/r.js for usage.

    $ rjs -v
    [info] r.js: 2.1.8, RequireJS: 2.1.8, UglifyJS2: 2.3.6, UglifyJS: 1.3.4

    $ rjs -o build.js
    [info] Tracing dependencies for: main
    [info]
    [info] main.js
    [info] ----------------
    [info] lib/jquery.js
    [info] main.js

    $ rjs -o 404.js
    [error] Error: Error: ERROR: build file does not exist: ~/project/404.js
    [error] javax.script.ScriptException: Error: Error: ERROR: build file does not exist: ~/project/404.js in <Unknown Source> at line number 2918
    [error] Use 'last' for the full log.

## API

### RequireJs Engine

A RequireJsEngine uses the javax.script API to run require.js optimizer, r.js, as a pre-compiled script. It will
adequately throw an exception if the optimizer fails or throws a JavaScript exception. Usage:

    // Sets up a ScriptEngine and compiles r.js
    val engine = new RequireJsEngine

    engine.run("-v")
    // Outputs:
    // [info] r.js: 2.1.8, RequireJS: 2.1.8, UglifyJS2: 2.3.6, UglifyJS: 1.3.4

    engine.run("-o", "build.js")
    // Alternatively:
    engine.build("build.js")
    // Outputs e.g:
    // [info] Tracing dependencies for: main
    // [info]
    // [info] main-min.js
    // [info] ----------------
    // [info] lib/jquery.js
    // [info] main.js

    // To use your own logger
    val logger = new sbt.Logger { ... }
    engine.run(Array("-o", "build.js"), logger)

### RequireJs Compiler

A RequireJsCompiler has convenient methods to continuously build a single project. The project source and target directories can
be set programmatically. It does not support single-file optimization projects. Usage:

    val compiler = RequireJsCompiler(new File("build.js"))

Or override/set the source and target directories

    val compiler = RequireJsCompiler(
        sourceDir = new File("sources"),
        targetDir = new File("target"),
        buildFile = new File("build.js"))

To build the project use one of the `build`, `buildModules`, `buildConfig`, and `devBuild` methods. Their return type
 is `sbt.Relation[File, File]` representing dependency relations between source and target files.

    // Build every modules defined in the build file
    compiler.build()

    // Build a set of modules. If the build file defines a configuration object for a module id in the set then it is reused.
    // Otherwise a simple object { name: "my/module/id> } is added to the modules definitions.
    compiler.buildModules(Set("my/module/id"))




[require]: http://requirejs.org/
[play]: http://www.playframework.org/
[sbt]: https://github.com/harrah/xsbt
[sbt-rjs]: https://github.com/sbt/sbt-rjs#sbt-rjs
[sbt-web]: https://github.com/sbt/sbt-web#sbt-web
[build.js]: https://github.com/jrburke/r.js/blob/master/build/example.build.js
