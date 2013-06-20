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
    val buildTask2 = TaskKey[Seq[File]]("play-requirejs-build2")
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
    /*  RequireCompiler.compile(
        sourceBuild = buildFile,
        targetBuild = resources / "public" / "build.js",
        sourceDir = resources / "public" / folder,
        targetDir = classes / "public" / folder
      )*/
    }
  }

  val requireResourceGenerator2: Project.Initialize[Task[Seq[File]]] = AssetsCompiler(
    "require",
    { file => (file ** "*") filter { _.isFile } },
    assets,
    { (name, _) => name },
    { (file, _) => (IO.read(file), None, Seq.empty) },
    options
  )

  val requireResourceGenerator: Project.Initialize[Task[Seq[File]]] = RequireCompilerC(
    _ => ("t", Seq.empty[File])

  )

  lazy val requireBaseSettings = Seq (
    folder := "javascripts-require",
    assets <<= (sourceDirectory in Compile, folder)((sources, folder) => sources / "assets" / folder ** "*"),
    buildFile <<= baseDirectory(_ / "project" / "build.js"),
    buildTask <<= requireBuildTask,
    buildTask2 <<= requireResourceGenerator,

    options := Seq.empty,
    resourceGenerators in Compile <+= requireResourceGenerator,
    javascriptEntryPoints <<= (javascriptEntryPoints, sourceDirectory in Compile, folder)(
      (entryPoints, sources, folder) => (entryPoints --- (sources / "assets" / folder ** "*"))
    ),
    (packageBin in Compile) <<= (packageBin in Compile).dependsOn(requireBuildTask)
  )

  def RequireCompilerC(compile: File => (String, Seq[File])): Project.Initialize[Task[Seq[File]]] =
    (state,
      baseDirectory,
      cacheDirectory,
      resourceManaged in Compile,
      buildFile) map { (state, base, cache, resources, buildFile) =>

      val cacheFile = cache / "require"
      val assets = (base / "app" / "assets" / "javascripts" ** "*")
      val currentInfos: Map[File, ModifiedFileInfo] = assets.get.map(f => f -> FileInfo.lastModified(f)).toMap

      val (previousRelation, previousInfo) = Sync.readInfo(cacheFile)(FileInfo.lastModified.format)

      if (previousInfo != currentInfos) {

        //a changed file can be either a new file, a deleted file or a modified one
        lazy val changedFiles: Seq[File] = currentInfos.filter(e => !previousInfo.get(e._1).isDefined || previousInfo(e._1).lastModified < e._2.lastModified).map(_._1).toSeq ++ previousInfo.filter(e => !currentInfos.get(e._1).isDefined).map(_._1).toSeq

        //erease dependencies that belong to changed files
        val dependencies = previousRelation.filter((original, compiled) => changedFiles.contains(original))._2s
        dependencies.foreach(IO.delete)

        /**
         * If the given file was changed or
         * if the given file was a dependency,
         * otherwise calculate dependencies based on previous relation graph
         */
        /*val generated: Seq[(File, File)] = (assets x relativeTo(Seq(base / "app" / "assets"))).flatMap {
          case (sourceFile, name) => {
            if (changedFiles.contains(sourceFile) || dependencies.contains(new File(resources, "public/" + name))) {
              val (compiled, dependencies) = try {
                compile(sourceFile)
              } catch {
                case e: AssetCompilationException => throw new Exception("")//PlaySourceGenerators.reportCompilationError(state, e)
              }
              //val out = new File(resources, "public/" + name)
              (dependencies ++ Seq(sourceFile)).toSet[File].map(_ -> compiled)
            } else {
              previousRelation.filter((original, compiled) => original == sourceFile)._2s.map(sourceFile -> _)
            }
          }
        }*/

        val generated = Seq.empty[(File, File)]

        //write object graph to cache file
        Sync.writeInfo(cacheFile,
          Relation.empty[File, File] ++ generated,
          currentInfos)(FileInfo.lastModified.format)

        // Return new files
        generated.map(_._2).distinct.toList

      } else {
        // Return previously generated files
        previousRelation._2s.toSeq
      }

    }

}
