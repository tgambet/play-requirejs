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
    val compiler   = SettingKey[RequireJsCompiler]("requirejs-compiler")
    val buildTask  = TaskKey[Seq[File]]("requirejs-build")
    val clearTask  = TaskKey[Unit]("requirejs-clear")
  }

  import RequireJS._

  lazy val requireCompiler: Project.Initialize[RequireJsCompiler] = (
    sourceDir,
    targetDir,
    buildFile,
    cacheFile,
    baseDir,
    streams).apply{(sourceDir, targetDir, buildFile, cacheFile, baseDir, s) =>

    new RequireJsCompiler(
      source = sourceDir,
      target = targetDir,
      buildFile = buildFile,
      buildDir = baseDir)

  }

  lazy val requireBuildTask: Project.Initialize[Task[Seq[File]]] =
    (compiler, targetDir, cacheFile, streams) map { (compiler, targetDir, cacheFile, s) =>
      compiler.devBuild(cacheFile)
      // return all created files directly. compiler.build()._2.toSeq should be equivalent.
      (targetDir ** "*").get
    }

  lazy val requireClearTask = (cacheFile) map { IO.delete(_) }

  lazy val requireBaseSettings = Seq (
    baseDir   <<= baseDirectory,
    buildFile <<= baseDirectory(_ / "project" / "build.js"),
    cacheFile <<= cacheDirectory(_ / "requirejs"),
    compiler  <<= requireCompiler,
    buildTask <<= requireBuildTask,
    clearTask <<= requireClearTask,
    resourceGenerators in Compile <+= requireBuildTask

    /*resourceGenerators in Compile <+= requireResourceGenerator,
    javascriptEntryPoints <<= (javascriptEntryPoints, sourceDirectory in Compile, folder)(
      (entryPoints, sources, folder) => (entryPoints --- (sources / "assets" / folder ** "*"))
    ),*/
    //(packageBin in Compile) <<= (packageBin in Compile).dependsOn(requireBuildTask)
  )
}