package net.tgambet.requirejs

import org.scalatest.FunSpec
import sbt._

class CachedRequireJsCompilerTest extends FunSpec with UseCases {

  describe("A CachedRequireJsCompiler") {

    withUseCase("use_case_3"){ base =>

      val source = base / "js"

      val target = base / "js-build"

      val build = base / "build.js"

      val cache = base / "cache"

      IO.delete(cache)

      val compiler = new CachedRequireJsCompiler(
        source = source,
        target = target,
        buildFile = build,
        buildDir = base,
        cacheFile = cache
      )

      it("should compile all modules the first time") {

        assert {
          compiler.build().all.toSet === Set(
            (source / "front.js"            -> target / "front.js"),
            (source / "app" / "app.js"      -> target / "front.js"),
            (source / "lib" / "backbone.js" -> target / "front.js"),
            (source / "app" / "app.js"      -> target / "back.js"),
            (source / "back.js"             -> target / "back.js"),
            (source / "lib" / "jquery.js"   -> target / "back.js"),
            (source / "app" / "admin.js"    -> target / "back.js")
          )
        }

      }

      it("should only compile modules that have dependencies changed since last build then") {

        IO.touch(source / "app" / "admin.js")

        assert {
          compiler.build().all.toSet === Set(
            (source / "back.js"           -> target / "back.js"),
            (source / "app" / "app.js"    -> target / "back.js"),
            (source / "app" / "admin.js"  -> target / "back.js"),
            (source / "lib" / "jquery.js" -> target / "back.js")
          )
        }

      }

      it("shouldn't compile anything if no dependencies have changed") {

/*        import FileFilter._

        val previousInfo: Map[File, ModifiedFileInfo] = {
          val assets = ((PathFinder(target) ** "*") filter (_.isFile))
          assets.get.map(f => f.getAbsoluteFile -> FileInfo.lastModified(f)).toMap
        }*/

        assert {
          compiler.build().all.toSet === Set()
        }

        IO.touch(source / "lib" / "underscore.js")

        assert {
          compiler.build().all.toSet === Set()
        }

        assert {
          compiler.build().all.toSet === Set()
        }

/*        val currentInfo: Map[File, ModifiedFileInfo] = {

          val assets = ((PathFinder(target) ** "*") filter (_.isFile))
          assets.get.map(f => f.getAbsoluteFile -> FileInfo.lastModified(f)).toMap
        }*/

/*        val changedFiles: Seq[File] =
          currentInfo.filter(e => !previousInfo.get(e._1).isDefined || previousInfo(e._1).lastModified < e._2.lastModified).map(_._1).toSeq ++
          previousInfo.filter(e => !currentInfo.get(e._1).isDefined).map(_._1).toSeq

        println(changedFiles)*/

      }

      it("should recompile everything if the build file has changed") {

        IO.touch(build)

        assert {
          compiler.build().all.toSet === Set(
            (source / "front.js"            -> target / "front.js"),
            (source / "app" / "app.js"      -> target / "front.js"),
            (source / "lib" / "backbone.js" -> target / "front.js"),
            (source / "app" / "app.js"      -> target / "back.js"),
            (source / "back.js"             -> target / "back.js"),
            (source / "lib" / "jquery.js"   -> target / "back.js"),
            (source / "app" / "admin.js"    -> target / "back.js")
          )
        }
      }

    }

  }

}
