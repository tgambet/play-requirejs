package org.github.tgambet

import sbt._
import sbt.Keys._
import play.Project._

object RequirePlugin extends Plugin {

  object RequireJS {
    val folder    = SettingKey[String]("play-requirejs-folder")
    val assets    = SettingKey[PathFinder]("play-requirejs-assets")
    val options   = SettingKey[Seq[String]]("play-requirejs-options")
    val output    = SettingKey[File]("play-requirejs-output-directory")
    val source    = SettingKey[File]("play-requirejs-source-directory")
    val buildFile = SettingKey[File]("play-requirejs-build-file")
    val build     = TaskKey[Unit]("play-requirejs-build")
    lazy val baseSettings = requireBaseSettings
  }

  import RequireJS._

  val requireBuildTask = (
    folder,
    resourceManaged in Compile,
    classDirectory in Compile,
    source,
    output,
    buildFile,
    baseDirectory,
    cacheDirectory,
    streams,
    copyResources in Compile) map { (folder, rm, classes, source, output, buildFile, base, cache, s, cp) =>

    //cp

    if (!buildFile.exists) {
      s.log.error("Require.js build file not found, expected: " + buildFile)
    } else {
      RequireCompiler.compile(
        sourceBuild = buildFile,
        targetBuild = rm / "public" / "build.js",
        sourceDir = rm / "public" / folder,
        targetDir = classes / "public" / folder
      )
    }
  }

  val requireResourceGenerator = AssetsCompiler(
    "require",
    { file => (file ** "*") filter { _.isFile } },
    assets,
    { (name, _) => name },
    { (file, _) => (IO.read(file), None, Seq.empty) },
    options
  )

  lazy val requireBaseSettings = Seq (
    options := Seq.empty,
    folder := "javascripts-require",
    assets <<= (sourceDirectory in Compile, folder)((sources, folder) => sources / "assets" / folder ** "*"),
    source <<= (resourceManaged in Compile, folder)((resources, folder) => resources / "public" / folder),
    output <<= (classDirectory  in Compile, folder)((classes, folder) => classes / "public" / folder),
    buildFile <<= baseDirectory(_ / "project" / "build.js"),
    build <<= requireBuildTask,
    resourceGenerators in Compile <+= requireResourceGenerator,
    javascriptEntryPoints <<= (javascriptEntryPoints, sourceDirectory in Compile, folder)(
      (entryPoints, sources, folder) => (entryPoints --- (sources / "assets" / folder ** "*"))
    ),
    (packageBin in Compile) <<= (packageBin in Compile).dependsOn(requireBuildTask)
  )



}
