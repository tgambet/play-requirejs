package org.github.tgambet

import sbt._
import sbt.Keys._
import play.Project._

object RequireJSPlugin extends Plugin {

  object RequireJS {
    val folder    = SettingKey[String]("play-requirejs-folder")
    val assets    = SettingKey[PathFinder]("play-requirejs-assets")
    val options   = SettingKey[Seq[String]]("play-requirejs-options")
    val output    = SettingKey[File]("play-requirejs-output-directory")
    val source    = SettingKey[File]("play-requirejs-source-directory")
    val buildFile = SettingKey[File]("play-requirejs-build-file")
    val build     = TaskKey[Unit]("play-requirejs-build")
    val baseSettings = baseRequireJSSettings
  }

  import RequireJS._

  val buildTask = (
    source,
    output,
    buildFile,
    baseDirectory,
    cacheDirectory,
    streams,
    copyResources in Compile) map { (source, output, buildFile, base, cache, s, _) =>

    if (!buildFile.exists) {
      s.log.error("Require.js build file not found, expected: " + buildFile)
    } else {
      val build = RequireCompiler.generateBuildConfig(
        buildFile = buildFile.getAbsoluteFile,
        sourceDir = source.getAbsoluteFile,
        outputDir = output.getAbsoluteFile
      )
      val cached = cache / "build.js"
      IO.write(cached, build.toString())
      RequireCompiler.compile(cached)
    }
  }

  val requireJSResourceGenerator = AssetsCompiler(
    "require",
    { file => (file ** "*") filter { _.isFile } },
    assets,
    { (name, _) => name },
    { (file, _) => (IO.read(file), None, Seq.empty) },
    options
  )

  val baseRequireJSSettings = Seq (
    folder := "javascripts",
    assets <<= (sourceDirectory in Compile, folder)((sources, folder) => sources / "assets" / folder ** "*"),
    options := Seq.empty,
    output <<= (classDirectory in Compile, folder)((classes, folder) => classes / "public" / folder),
    source <<= (resourceManaged in Compile, folder)((resources, folder) => resources / "public" / folder),
    buildFile <<= baseDirectory(_ / "project" / "build.js"),
    build <<= buildTask,
    resourceGenerators in Compile <+= requireJSResourceGenerator,
    javascriptEntryPoints <<= (javascriptEntryPoints, sourceDirectory in Compile, folder)(
      (entryPoints, base, folder) => (entryPoints --- (base / "assets" / folder ** "*"))
    ),
    (packageBin in Compile) <<= (packageBin in Compile).dependsOn(buildTask)
  )



}
