
package org.github.tgambet

import org.scalatest.FunSpec
import sbt._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import java.nio.charset.Charset

class RequireCompilerSpec extends FunSpec {

  val target = new File("target/tests")
  val resources = new File("src/test/resources/")

  def runUseCase(useCase: String)(f: Function[File, Any]) = {
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

      assert {
        load(buildJs).values.get("modules") match {
          case Some(obj: List[_]) => obj.size == 2
          case _ => false
        }
      }
    }

    it ("should compile a project given a build.json file and produce a map of dependencies") {

      import RequireCompiler.compile

      runUseCase("use_case_1")(sources =>
        assert {
          compile(sources / "build.js").all.toSet === Set.empty[(File, File)] // TODO
        }
      )

      runUseCase("use_case_2"){sources =>
        val target = sources / "built"
        assert {
          compile(sources / "build.js").all.toSet === Set(
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

      import RequireCompiler.compile

      runUseCase("use_case_2"){ sources =>
        val target = sources / "built"
        assert {
          compile(sources / "build.js", List("front", "back")).all.toSet === Set(
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
          compile(sources / "build.js", List("back")).all.toSet === Set(
            (sources / "back.js"           -> target / "back.js"),
            (sources / "app" / "app.js"    -> target / "back.js"),
            (sources / "app" / "admin.js"  -> target / "back.js"),
            (sources / "lib" / "jquery.js" -> target / "back.js")
          )
        }
      }

    }

    it ("should track changes to deps") {

      import RequireCompiler.recompile

      runUseCase("use_case_2"){ sources =>

        val target = sources / "built"
        val cacheFile = target / "cache"

        IO.delete(cacheFile)

        assert {
          recompile(sources / "build.js", cacheFile).all.toSet === Set(
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
          recompile(sources / "build.js", cacheFile).all.toSet === Set(
            (sources / "back.js"           -> target / "back.js"),
            (sources / "app" / "app.js"    -> target / "back.js"),
            (sources / "app" / "admin.js"  -> target / "back.js") ,
            (sources / "lib" / "jquery.js" -> target / "back.js")
          )
        }
      }

    }

  }
}

