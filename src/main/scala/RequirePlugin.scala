package org.github.tgambet

import sbt._
import sbt.Keys._
import play.Project._
import util.parsing.json.{JSONObject, JSON}

object RequirePlugin extends Plugin {

  val requireAssets    = SettingKey[PathFinder]("play-requirejs-assets")
  val requireOptions   = SettingKey[Seq[String]]("play-requirejs-options")
  val requireBuildFile = SettingKey[File]("play-requirejs-build-file")
  val requireDirectory = SettingKey[String]("play-requirejs-directory")

  val requireWatcher = AssetsCompiler(
      "require",
      { file => (file ** "*") filter { _.isFile } },
      requireAssets,
      { (name, _) => name },
      { (file, _) => (IO.read(file), None, Seq.empty) },
      requireOptions
  )

  val require = TaskKey[Unit]("play-requirejs-build", "Build require.js assets")

  val requireTask = (
    copyResources in Compile,
    baseDirectory,
    classDirectory in Compile,
    cacheDirectory,
    resourceManaged,
    requireBuildFile,
    streams,
    requireDirectory) map { (copyResources, base, classes, cache, resources, buildFile, s, directory) =>

    val source = resources / "main" / "public" / directory
    val target = classes / "public" / directory

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
              + (("dir", target.getAbsolutePath))
              + (("appDir", source.getAbsolutePath))
              + (("keepBuildDir", true)))
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

  override val settings = Seq(
    resourceGenerators in Compile <+= requireWatcher,
    requireDirectory := "javascripts",
    require <<= requireTask,
    requireAssets <<= (sourceDirectory in Compile)(_ / "assets" / "javascripts" ** "*"),
    requireOptions := Seq.empty,
    javascriptEntryPoints <<=(javascriptEntryPoints, sourceDirectory in Compile, requireDirectory)(
        (entryPoints, base, requireDir) => (entryPoints --- (base / "assets" / requireDir))
    ),
    requireBuildFile <<= baseDirectory(_ / "project" / "build.js")
  )

}
