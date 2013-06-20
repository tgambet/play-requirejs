
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
    val targetDir = target / useCase
    IO.delete(targetDir)
    IO.copyDirectory(srcDir, targetDir)
    f(targetDir)
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

      runUseCase("use_case_1")(targetDir =>
        assert {
          compile(targetDir / "build.js").all.toList === List.empty[(File, File)] // TODO
        }
      )

      runUseCase("use_case_2")(targetDir =>
        assert {
          compile(targetDir / "build.js").all.toList === List(
            ("app/app.js" -> "front.js"),
            ("app/app.js" -> "back.js"),
            ("back.js" -> "back.js"),
            ("lib/jquery.js" -> "back.js"),
            ("front.js" -> "front.js"),
            ("app/admin.js" -> "back.js"),
            ("lib/backbone.js" -> "front.js")
          ).map{case (a, b) => (new File(a), new File(b))}
        }
      )
    }

    it ("should track dependencies changes and rebuild affected modules only") {

      import RequireCompiler.compile

      runUseCase("use_case_2"){ targetDir =>
        assert {
          compile(targetDir / "build.js", List("front", "back")).all.toSet === Set(
            ("app/app.js" -> "front.js"),
            ("app/app.js" -> "back.js"),
            ("back.js" -> "back.js"),
            ("lib/jquery.js" -> "back.js"),
            ("front.js" -> "front.js"),
            ("app/admin.js" -> "back.js"),
            ("lib/backbone.js" -> "front.js")
          ).map{case (a, b) => (new File(a), new File(b))}
        }

        assert {
          compile(targetDir / "build.js", List("back")).all.toSet === Set(
            ("app/app.js" -> "back.js"),
            ("back.js" -> "back.js"),
            ("lib/jquery.js" -> "back.js"),
            ("app/admin.js" -> "back.js")
          ).map{case (a, b) => (new File(a), new File(b))}
        }
      }

    }

  }
}

