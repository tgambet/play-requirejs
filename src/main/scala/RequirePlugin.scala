package org.github.tgambet

import sbt._
import sbt.Keys._
import play.Project._

object RequirePlugin extends Plugin {

  object RequireJS {
    val folder    = SettingKey[String]("play-requirejs-folder")
    val assets    = SettingKey[PathFinder]("play-requirejs-assets")
    val buildFile = SettingKey[File]("play-requirejs-build-file")
    val buildTask = TaskKey[Unit]("play-requirejs-build")
    lazy val baseSettings = requireBaseSettings
  }

  import RequireJS._

  private val options = SettingKey[Seq[String]]("play-requirejs-options")

  val requireBuildTask = (
    folder,
    buildFile,
    resourceManaged in Compile,
    classDirectory in Compile,
    streams,
    copyResources in Compile) map { (folder, buildFile, resources, classes, s, _) =>

    if (!buildFile.exists) {
      s.log.error("Require.js build file not found, expected: " + buildFile)
    } else {
      RequireCompiler.compile(
        sourceBuild = buildFile,
        targetBuild = resources / "public" / "build.js",
        sourceDir = resources / "public" / folder,
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
    folder := "javascripts-require",
    assets <<= (sourceDirectory in Compile, folder)((sources, folder) => sources / "assets" / folder ** "*"),
    buildFile <<= baseDirectory(_ / "project" / "build.js"),
    buildTask <<= requireBuildTask,

    options := Seq.empty,
    resourceGenerators in Compile <+= requireResourceGenerator,
    javascriptEntryPoints <<= (javascriptEntryPoints, sourceDirectory in Compile, folder)(
      (entryPoints, sources, folder) => (entryPoints --- (sources / "assets" / folder ** "*"))
    ),
    (packageBin in Compile) <<= (packageBin in Compile).dependsOn(requireBuildTask)
  )

}
