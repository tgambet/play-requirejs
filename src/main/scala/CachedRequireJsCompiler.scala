package net.tgambet

import sbt.{IO, File, Relation, ModifiedFileInfo, FileFilter, FileInfo, Sync, PathFinder}
import org.json4s.JsonAST.JObject

import org.json4s.JsonDSL._
import RequireJsCompiler._
import FileImplicits._

class CachedRequireJsCompiler(
  source: File,
  target: File,
  buildFile: File,
  buildDir: File,
  val cacheFile: File) extends RequireJsCompiler(source, target, buildFile, buildDir) {

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
          println("Copying asset: " + file)
          Sync.copy(source / file, target / file)
        }
        Sync.writeInfo(cacheFile, previousRelation, currentInfo)(FileInfo.lastModified.format)
        Relation.empty[File, File]
      }

    } else {

      println("Rebuilding everything")

      val res = super.build(config ~ ("keepBuildDir" -> false))
      Sync.writeInfo(cacheFile, res, currentInfo)(FileInfo.lastModified.format)
      res
    }

  }

}
