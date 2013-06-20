
package org.github.tgambet

import org.scalatest.FunSpec
import java.io.File
import sbt._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.JsonAST.{JField, JObject}
import sbt.Keys._

class RequireCompilerSpec extends FunSpec {



  describe("RequireCompiler") {

    /*it("should load require.js build files written in js or json syntax") {

      import RequireCompiler.load

      val target = new File("target/tests")
      val resources = new File("src/test/resources/")
      val assets = resources / "js"

      val buildJs   = resources / "builds" / "build.js"
      val buildJson = resources / "builds" / "build.json"

      assert(load(buildJs) === load(buildJson))

      assert {
        load(buildJs).get("modules") match {
          case Some(obj: List[_]) => obj.size == 2
          case _ => false
        }
      }

    }*/

    it ("should compile a project given a build.json file and produce a map of dependencies") {

      //import RequireCompiler.load

      val target = new File("target/tests")
      val resources = new File("src/test/resources/")
      val assets = resources / "js"

      val useCase = "use_case_2"

      //val build = load(resources / useCase / "build.json")
      val srcDir = resources / useCase
      val targetDir = target / useCase

      IO.delete(targetDir)
      IO.copyDirectory(srcDir, targetDir)
      //IO.copyFile(targetDir / "build.json", targetDir / "build.js")
      assert{
        RequireCompiler.compile(targetDir / "build.js").all.toList === List(
          ("app/app.js" -> "front.js"),
          ("app/app.js" -> "back.js"),
          ("back.js" -> "back.js"),
          ("lib/jquery.js" -> "back.js"),
          ("front.js" -> "front.js"),
          ("app/admin.js" -> "back.js"),
          ("lib/backbone.js" -> "front.js")
        ).map{case (a, b) => (new File(a), new File(b))}

      }

    }


/*    it("should compile") {

      //IO.delete(target)

      //val builds = buildFiles map { build =>

      val newb: JObject = ("dir" -> "target/tests/") ~
        ("appDir" -> "src/test/resources/js") ~
        ("baseUrl" -> ".") ~
        ("modules" -> List(("name" -> "test")))

      val build = build1

        RequireCompiler.compile(
          sourceDir = sources,
          targetDir = target / (build.getName + "-out"),
          sourceBuild = build,
          targetBuild = target / build.getName
        )
      //}

    }*/

  }
}

