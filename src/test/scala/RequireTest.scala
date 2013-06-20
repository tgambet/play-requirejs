/*
import org.scalatest.FunSpec
import java.io.File
import sbt._
import org.json4s.JsonDSL._
import org.json4s.JsonAST.{JField, JObject}

class RequireTest extends FunSpec {

  val cache = new File("target/cache-tests")
  val target = new File("target/tests")
  val resources = new File("src/test/resources/")
  val sources = resources / "js"
  val build1 = resources / "build-1.js"
  val build2 = resources / "build-2.js"
  val build3 = resources / "build-3.json"
  val buildFiles = Seq(
    build1,
    build2,
    build3
  )

  describe("RequireCompiler") {

    it("should") {

      val cacheFile = cache / "require"
      val assets = (resources / "js" ** "*")
      val currentInfos: Map[File, ModifiedFileInfo] = assets.get.map(f => f.getAbsoluteFile -> FileInfo.lastModified(f)).toMap

      //println(assets.get)
      println(cacheFile)
      println(currentInfos)

      val (previousRelation, previousInfo) = Sync.readInfo(cacheFile)(FileInfo.lastModified.format)

      println("%%%%%%%%%%%%%")
      println(previousInfo)

      if (previousInfo != currentInfos) {

        println("########### CHANGED ############")

        //a changed file can be either a new file, a deleted file or a modified one
        lazy val changedFiles: Seq[File] = currentInfos.filter(e => !previousInfo.get(e._1).isDefined || previousInfo(e._1).lastModified < e._2.lastModified).map(_._1).toSeq ++ previousInfo.filter(e => !currentInfos.get(e._1).isDefined).map(_._1).toSeq

        println(changedFiles)

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
            if (changedFiles.contains(sourceFile) || dependencies.contains(new File(resourcesManaged, "public/" + name))) {
              val (compiled, dependencies) = try {
                compile(sourceFile)
              } catch {
                case e: AssetCompilationException => throw new Exception("")//PlaySourceGenerators.reportCompilationError(state, e)
              }
              //val out = new File(resourcesManaged, "public/" + name)
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


}

*/
