package net.tgambet.requirejs

import sbt._
import sbt.Keys._

object RequireJsPlugin extends Plugin {

  object RequireJS {
    val sourceDir  = SettingKey[File]("requirejs-source-dir")
    val targetDir  = SettingKey[File]("requirejs-target-dir")
    val buildFile  = SettingKey[File]("requirejs-build-file")
    val cacheFile  = SettingKey[File]("requirejs-cache-file")
    val baseDir    = SettingKey[File]("requirejs-base-dir")
    val buildTask  = TaskKey[Seq[File]]("requirejs-build")
    val cleanTask  = TaskKey[Unit]("requirejs-clean")
  }

  import RequireJS._

  lazy val requireJsEngine = new RequireJsEngine

  lazy val requireBuildTask = (
    sourceDir,
    targetDir,
    buildFile,
    cacheFile,
    baseDir,
    streams) map {(sourceDir, targetDir, buildFile, cacheFile, baseDir, s) =>

      val compiler = new RequireJsCompiler(
        source = sourceDir,
        target = targetDir,
        buildFile = buildFile,
        buildDir = baseDir,
        engine = requireJsEngine,
        logger = s.log
      )

      compiler.devBuild(cacheFile)
      // return all created files directly. compiler.build()._2.toSeq should be equivalent.
      (targetDir ** "*").get
    }

  lazy val requireCleanTask = (cacheFile) map { IO.delete(_) }

  lazy val rjs: Command = Command.args("rjs", "<arg>")((s, args) => {
    requireJsEngine.run(args.toArray, s.globalLogging.full)
    s
  })

  override def settings = Seq(
    commands += rjs
  )

  lazy val requireBaseSettings = Seq (
    baseDir   <<= baseDirectory,
    buildFile <<= baseDirectory(_ / "project" / "build.js"),
    cacheFile <<= cacheDirectory(_ / "requirejs"),
    buildTask <<= requireBuildTask,
    cleanTask <<= requireCleanTask,
    resourceGenerators in Compile <+= requireBuildTask

    /*resourceGenerators in Compile <+= requireResourceGenerator,
    javascriptEntryPoints <<= (javascriptEntryPoints, sourceDirectory in Compile, folder)(
      (entryPoints, sources, folder) => (entryPoints --- (sources / "assets" / folder ** "*"))
    ),*/
    //(packageBin in Compile) <<= (packageBin in Compile).dependsOn(requireBuildTask)
  )
}