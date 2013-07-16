play-requirejs
=========

[Require.js][require] optimizer plugin for [sbt][sbt]. Initially meant as a [Play][play] plugin but it is not
required to use it.

## Use with sbt

...

## Use with Play

...

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