package net.tgambet.requirejs

// don't import sbt._ because we don't want the file implicits
import sbt.File
import sbt.Relation
import sbt.ModifiedFileInfo
import sbt.FileFilter
import sbt.FileInfo
import sbt.Sync
import sbt.PathFinder

import org.slf4j.{LoggerFactory, Logger}
import org.json4s.JsonAST.JObject

import org.json4s.JsonDSL._
import RequireJsCompiler._
import FileImplicits._

object CachedRequireJsCompiler {

  val logger = LoggerFactory.getLogger(classOf[CachedRequireJsCompiler])

}

class CachedRequireJsCompiler(
  source: File,
  target: File,
  buildFile: File,
  buildDir: File,
  val cacheFile: File) extends RequireJsCompiler(source, target, buildFile, buildDir) {

  import CachedRequireJsCompiler._

  override def build(config: Config) : Relation[File, File] = {

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

        val newConfig = configForModules(config, modules) merge (JObject() ~ ("keepBuildDir", true))
        val res = super.build(newConfig)
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

      val res = super.build(config ~ ("keepBuildDir" -> false))
      Sync.writeInfo(cacheFile, res, currentInfo)(FileInfo.lastModified.format)
      res
    }

  }

}
