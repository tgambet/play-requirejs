package net.tgambet.requirejs

import org.json4s._
import org.json4s.JsonAST.JValue
import java.io.File
import sbt.IO
import sbt.Relation
import sbt.Logger
import sbt.ModifiedFileInfo
import sbt.FileFilter
import sbt.FileInfo
import sbt.Sync
import sbt.PathFinder
import scala.util.matching.Regex

import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import FileImplicits._

object RequireJsCompiler {

  type Config = JObject

  def load(file: File): Config = {

    def jsonify(jsContent: String): String = {
      val replacements: Seq[(Regex, String)] = Seq(
        ("""(?s)(/\*.*?\*/)""".r -> ""),    // remove multi-line comments
        ("""(//.*)""".r -> ""),             // remove single-line comments
        ("""(?m)^\s*\n""".r -> ""),         // remove empty lines
        ("""'""".r -> "\""),                // single to double quotes
        ("""(\w+):""".r -> "\"$1\":"),      // quote unquoted keys
        ("""(?s)\A\((.*)\)\z""".r -> "$1")  // remove wrapping parenthesis
      )
      replacements.foldLeft(jsContent){ case (content, (regex, replace)) =>
        regex.replaceAllIn(content.trim, replace)
      }
    }

    try {
      val content = IO.read(file)
      parse(if (file.getName.endsWith(".js")) jsonify(content) else content).asInstanceOf[JObject]
    } catch {
      case e: Exception => throw new RequireJsException("Error loading buildFile: " + file + " (" + e.getMessage + ")", e)
    }

  }

  def parseReport(report: File, source: File, target: File): Relation[File, File] = {
    val content = IO.read(report)
    val moduleDependencies = content.split("""\n\n""").map{case a =>
      val arr = a.split("\n----------------\n")
      if (arr.length == 2) {
        val module = arr(0).trim
        val deps = arr(1).split("""\n""")
        deps /*map ((_, module))*/ map{
          case dep => ((source / dep).normalize, (target / module).normalize)
        }
      } else {
        Array.empty[(File, File)]
      }
    }.flatten
    Relation.empty[File, File] ++ moduleDependencies
  }

  /**
   *
   * @param buildFile A valid require.js build file
   * @return A RequireJsCompiler backed by a build file
   */
  def apply(buildFile: File): RequireJsCompiler = {
    val config = load(buildFile)
    val buildDir = buildFile.parent
    val dir = asFile(config \ "dir").getOrElse {
      throw new Exception("No target directory ('dir' option) found in the build file " + buildFile)
    }
    val appDir = asFile(config \ "appDir").getOrElse {
      throw new Exception("No source directory ('appDir' option) found in the build file " + buildFile)
    }
    new RequireJsCompiler(
      buildDir = buildDir,
      source = buildDir / appDir,
      target = buildDir / dir,
      buildFile = buildFile
    )
  }

  /**
   *
   * @param sourceDir
   * @param targetDir
   * @param buildFile
   * @return
   */
  def apply(sourceDir: File, targetDir: File, buildFile: File = file("build.js")): RequireJsCompiler = {
    val buildDir = buildFile.parent
    new RequireJsCompiler(
      buildDir = buildDir,
      source = sourceDir,
      target = targetDir,
      buildFile = buildFile
    )
  }

  // Get a config value as a File
  private def asFile: JValue => Option[File] = {
    value => value match {
      case JString(p) => Some(file(p))
      case _ => None
    }
  }

}

class RequireJsCompiler(
   val source: File,
   val target: File,
   val buildFile: File,
   val buildDir: File,
   val engine: RequireJsEngine = new RequireJsEngine,
   val logger: Logger = SystemLogger) {

  import RequireJsCompiler._

  if (target isChildOf source)
    throw new RequireJsException("Failed to create a compiler: the target directory (" + target + ") cannot be a child of the source directory (" + source + ")")

  def build(): Relation[File, File] = buildConfig(load(buildFile))

  def buildModules(moduleIds: Set[String]): Relation[File, File] = buildConfig(configForModules(load(buildFile), moduleIds))

  def buildConfig(config: Config): Relation[File, File] = {

    logger.info("Build started")
    logger.debug("- Source directory: " + source.toPath)
    logger.debug("- Target directory: " + target.toPath)
    logger.debug("- Build file path: " + buildFile.toPath)
    logger.debug("- Build directory: " + buildDir)

    val newBuild = buildDir / ".build.tmp.js"

    val newJson = {

      val defaults =
        ("baseUrl" -> "./") ~ // r.js throws an unhelpful exception when this is not set so let's make it a default
        ("logLevel" -> 0)

      val overrides =
        ("dir"    -> (target relativeTo buildDir).toString) ~
        ("appDir" -> (source relativeTo buildDir).toString)

      defaults merge config merge overrides
    }

    logger.debug("Writing to temporary build file: " + newBuild)

    IO.write(newBuild, pretty(render(newJson)))

    logger.debug(IO.read(newBuild))

    engine.build(newBuild, logger)

    val report = target / "build.txt"

    val r = parseReport(report, source, target)

    IO.delete(report)

    r
  }

  def devBuild(cacheFile: File): Relation[File, File] = {

    val config = load(buildFile)

    val currentInfo: Map[File, ModifiedFileInfo] = {
      import FileFilter._
      val assets = ((PathFinder(source) ** "*") filter (_.isFile)) +++ buildFile
      assets.get.map(f => f.getAbsoluteFile -> FileInfo.lastModified(f)).toMap
    }

    val (previousRelation, previousInfo) = Sync.readInfo(cacheFile)(FileInfo.lastModified.format)

    lazy val changedFiles: Seq[File] =
      currentInfo.filter(e => !previousInfo.get(e._1).isDefined || previousInfo(e._1).lastModified < e._2.lastModified).map(_._1).toSeq ++
        previousInfo.filter(e => !currentInfo.get(e._1).isDefined).map(_._1).toSeq

    if (!previousInfo.isEmpty && !changedFiles.contains(buildFile.getAbsoluteFile)) {

      val affectedModules = previousRelation.all.collect {
        case (sourceF, module) if changedFiles.contains(sourceF) => module
      }

      val modules = affectedModules.map{f => (f relativeTo target).toString.replaceAll("""\.js$""", "").replaceAll("""\\""", "/")}.toSet

      if (!modules.isEmpty) {

        val newConfig =
          configForModules(config, modules) merge (("keepBuildDir", true) ~ ("optimize" -> "none"))

        val res = buildConfig(newConfig)
        Sync.writeInfo(cacheFile, res, currentInfo)(FileInfo.lastModified.format)
        res
      } else {

        val files = changedFiles.map(_ relativeTo source)
        files.foreach{file =>
          logger.info("Copying asset: " + file)
          Sync.copy(source / file, target / file)
        }
        Sync.writeInfo(cacheFile, previousRelation, currentInfo)(FileInfo.lastModified.format)
        Relation.empty[File, File]
      }

    } else {

      logger.info("Rebuilding everything")

      val res = buildConfig(config merge (("keepBuildDir" -> false) ~ ("optimize" -> "none")))
      Sync.writeInfo(cacheFile, res, currentInfo)(FileInfo.lastModified.format)
      res
    }

  }

  private def configForModules(config: Config, moduleIds: Set[String]): Config = {

    def obj(o: (String, JValue)): JObject = JObject() ~ o

    val modules: JArray = (config \ "modules") match {

      case JArray(modules) => {

        def findModule(name: String): Option[JObject] = modules.collect{
          case module: JObject if (module \ "name") == JString(name) => module
        }.headOption

        moduleIds.map { moduleId => findModule(moduleId) getOrElse obj("name" -> moduleId) }
      }

      case _ => moduleIds.map { moduleId => obj("name" -> moduleId) }
    }

    config.transformField{
      case JField("modules", _) => JField("modules", modules)
    } match {
      case a: JObject => a merge obj("modules", modules)
      case _ => sys.error("Not an object?")
    }
  }

}
