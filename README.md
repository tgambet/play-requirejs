play-requirejs
=========

[Require.js][require] asset handling plugin for [Play 2.x][play]. Implemented as [sbt][sbt] plugin.

Installation
------------

Download the code and run `play publish-local` to publish the jar in play's repository, with `play` on your PATH:

    ~/projects/play-require> play publish-local

Add the plugin to your project. In `project/plugins.sbt`:

    addSbtPlugin("org.github.tgambet" % "play-require" % "0.1")

Configure the plugin. In `project/Build.scala`:

    import org.github.tgambet.RequireJSPlugin._

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA)
        .settings( RequireJS.baseSettings: _* )
        .settings(
            // override default plugin settings
            RequireJS.folder <<= "javascripts-require"
        )

[Plugin settings](https://github.com/tgambet/play-requirejs/blob/master/src/main/scala/RequirePlugin.scala#L9)

[require]: http://requirejs.org/
[play]: http://www.playframework.org/
[sbt]: https://github.com/harrah/xsbt