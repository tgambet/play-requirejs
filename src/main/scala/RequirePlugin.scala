package org.github.tgambet

import sbt._
import sbt.Keys._
import play.Project._
import util.parsing.json.{JSONObject, JSON}

object RequireJSPlugin extends Plugin {

  object RequireJS {
    val folder    = SettingKey[String]("play-requirejs-folder")
    val assets    = SettingKey[PathFinder]("play-requirejs-assets")
    val options   = SettingKey[Seq[String]]("play-requirejs-options")
    val output    = SettingKey[File]("play-requirejs-output-directory")
    val source    = SettingKey[File]("play-requirejs-source-directory")
    val buildFile = SettingKey[File]("play-requirejs-build-file")
    val build     = TaskKey[Unit]("play-requirejs-build")
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
      s.log.error("Require.js build file not found")
    } else {
      val content = IO.read(buildFile)
      val json = JSON.parseRaw(RequireCompiler.jsonify(content))
      val newJson: JSONObject = json match {
        case Some(json: JSONObject) => {
          val obj = if (json.obj.isDefinedAt("mainConfigFile")) {
            json.obj + (("mainConfigFile", (source / json.obj("mainConfigFile").asInstanceOf[String]).getAbsolutePath))
          } else json.obj
          JSONObject(obj
              + (("dir", output.getAbsolutePath))
              + (("appDir", source.getAbsolutePath)))
              //+ (("keepBuildDir", true)))
        }
        case _ => {
          throw new Exception("Error parsing build file: \n" + buildFile)
        }
      }

      val cached = cache / "build.js"
      IO.write(cached, newJson.toString())
      RequireCompiler.compile(cached)
    }

  }

  private val resourceGenerator = AssetsCompiler(
    "require",
    { file => (file ** "*") filter { _.isFile } },
    assets,
    { (name, _) => name },
    { (file, _) => (IO.read(file), None, Seq.empty) },
    options
  )

  val baseSettings = Seq (
    folder := "javascripts",
    assets <<= (sourceDirectory in Compile, folder)((sources, folder) => sources / "assets" / folder ** "*"),
    options := Seq.empty,
    output <<= (classDirectory in Compile, folder)((classes, folder) => classes / "public" / folder),
    source <<= (resourceManaged in Compile, folder)((resources, folder) => resources / "public" / folder),
    buildFile <<= baseDirectory(_ / "project" / "build.js"),
    build <<= buildTask,
    resourceGenerators in Compile <+= resourceGenerator,
    javascriptEntryPoints <<= (javascriptEntryPoints, sourceDirectory in Compile, folder)(
      (entryPoints, base, folder) => (entryPoints --- (base / "assets" / folder ** "*"))
    ),
    (packageBin in Compile) <<= (packageBin in Compile).dependsOn(buildTask)
  )

}
