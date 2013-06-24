package net.tgambet

import org.scalatest.FunSpec
import sbt._
import org.json4s._

class RequireCompilerSpec extends FunSpec {

  val target = new File("target/tests")
  val resources = new File("src/test/resources/")

  def withUseCase(useCase: String)(f: Function[File, Any]) = {
    val srcDir = resources / useCase
    val newSrcDir = target / useCase
    IO.delete(newSrcDir)
    IO.copyDirectory(srcDir, newSrcDir)
    f(newSrcDir)
  }

  describe("RequireCompiler") {

    it("should load require.js build files written in js or json syntax") {

      import RequireCompiler.load

      val buildJs   = resources / "builds" / "build.js"
      val buildJson = resources / "builds" / "build.json"

      assert(load(buildJs) === load(buildJson))

      assert {
        load(buildJs) \ "modules" match {
          case JArray(mods) => mods.length == 2
          case _ => false
        }
      }
    }

    it ("should compile a project given a build.json file and produce a map of dependencies") {

      import RequireCompiler.compiler

      withUseCase("use_case_1"){sources =>
        assert {
          compiler(buildFile = Some(sources / "build.js")).all.toSet === Set(
            (sources / "lib" / "jquery.js" -> sources / "requirejs-build" / "main.js"),
            (sources / "main.js"   -> sources / "requirejs-build" / "main.js")
          )
        }
      }

      withUseCase("use_case_2"){sources =>
        val target = sources / "requirejs-build"
        assert {
          compiler(buildFile = Some(sources / "build.js")).all.toSet === Set(
            (sources / "front.js"            -> target / "front.js"),
            (sources / "app" / "app.js"      -> target / "front.js"),
            (sources / "lib" / "backbone.js" -> target / "front.js"),
            (sources / "app" / "app.js"      -> target / "back.js"),
            (sources / "back.js"             -> target / "back.js"),
            (sources / "lib" / "jquery.js"   -> target / "back.js"),
            (sources / "app" / "admin.js"    -> target / "back.js")
          )
        }
      }
    }

    it ("should accept a list of modules to be built") {

      import RequireCompiler.compiler

      withUseCase("use_case_2"){ sources =>
        val target = sources / "requirejs-build"
        assert {
          compiler(
            buildFile = Some(sources / "build.js"),
            modules = Some(Set("front", "back"))
            //
          ).all.toSet === Set(
            (sources / "front.js"            -> target / "front.js"),
            (sources / "app" / "app.js"      -> target / "front.js"),
            (sources / "lib" / "backbone.js" -> target / "front.js"),
            (sources / "app" / "app.js"      -> target / "back.js"),
            (sources / "back.js"             -> target / "back.js"),
            (sources / "lib" / "jquery.js"   -> target / "back.js"),
            (sources / "app" / "admin.js"    -> target / "back.js")
          )
        }

        assert {
          compiler(
            buildFile = Some(sources / "build.js"),
            modules = Some(Set("back"))
          ).all.toSet === Set(
            (sources / "back.js"           -> target / "back.js"),
            (sources / "app" / "app.js"    -> target / "back.js"),
            (sources / "app" / "admin.js"  -> target / "back.js"),
            (sources / "lib" / "jquery.js" -> target / "back.js")
          )
        }
      }

    }

    it ("should recompile only modules which dependencies have changed") {

      import RequireCompiler.compiler

      withUseCase("use_case_2"){ sources =>

        val target = sources / "requirejs-build"
        val cacheFile = target / "cache"

        IO.delete(cacheFile)

        assert {
          compiler(
            buildFile = Some(sources / "build.js"),
            cacheFile = Some(cacheFile)
          ).all.toSet === Set(
            (sources / "back.js"             -> target / "back.js"),
            (sources / "app" / "app.js"      -> target / "back.js"),
            (sources / "app" / "admin.js"    -> target / "back.js"),
            (sources / "lib" / "jquery.js"   -> target / "back.js"),
            (sources / "front.js"            -> target / "front.js"),
            (sources / "app" / "app.js"      -> target / "front.js"),
            (sources / "lib" / "backbone.js" -> target / "front.js")
          )
        }

        IO.touch(sources / "app" / "admin.js")

        assert {
          compiler(
            buildFile = Some(sources / "build.js"),
            cacheFile = Some(cacheFile)
          ).all.toSet === Set(
            (sources / "back.js"           -> target / "back.js"),
            (sources / "app" / "app.js"    -> target / "back.js"),
            (sources / "app" / "admin.js"  -> target / "back.js") ,
            (sources / "lib" / "jquery.js" -> target / "back.js")
          )
        }

        assert {
          compiler(
            buildFile = Some(sources / "build.js"),
            cacheFile = Some(cacheFile)
          ).all.toSet === Set()
        }
      }

    }

  }
}

