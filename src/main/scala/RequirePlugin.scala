package net.tgambet

import sbt._
import sbt.Keys._

object RequirePlugin extends Plugin {

  object RequireJS {
    val sourceDir  = SettingKey[File]("requirejs-source-dir")
    val targetDir  = SettingKey[File]("requirejs-target-dir")
    val buildFile  = SettingKey[File]("requirejs-build-file")
    val cacheFile  = SettingKey[File]("requirejs-cache-file")
    val baseDir    = SettingKey[File]("requirejs-base-dir")
    val compiler   = SettingKey[RequireCompiler]("requirejs-compiler")
    val buildTask  = TaskKey[Seq[File]]("requirejs-build")
  }

  import RequireJS._

  lazy val requireCompiler: Project.Initialize[RequireCompiler] = (
    sourceDir,
    targetDir,
    buildFile,
    cacheFile,
    baseDir,
    streams).apply{(sourceDir, targetDir, buildFile, cacheFile, baseDir, s) =>

    new CachedCompiler(
      source = sourceDir,
      target = targetDir,
      buildFile = buildFile,
      buildDir = baseDir,
      cacheFile = cacheFile)

  }

  lazy val requireBuildTask: Project.Initialize[Task[Seq[File]]] =
    (compiler, targetDir, streams) map { (compiler, targetDir, s) =>
      compiler.build()
      (targetDir ** "*").get
    }

  lazy val requireBaseSettings = Seq (
    baseDir   <<= baseDirectory,
    buildFile <<= baseDirectory(_ / "project" / "build.js"),
    cacheFile <<= cacheDirectory(_ / "requirejs"),
    compiler  <<= requireCompiler,
    buildTask <<= requireBuildTask,
    resourceGenerators in Compile <+= requireBuildTask
    //buildTask2 <<= requireResourceGenerator,

    /*resourceGenerators in Compile <+= requireResourceGenerator,
    javascriptEntryPoints <<= (javascriptEntryPoints, sourceDirectory in Compile, folder)(
      (entryPoints, sources, folder) => (entryPoints --- (sources / "assets" / folder ** "*"))
    ),*/
    //(packageBin in Compile) <<= (packageBin in Compile).dependsOn(requireBuildTask)
  )
}