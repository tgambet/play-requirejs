package org.github.tgambet

import org.scalatest.FunSpec
import java.io.File
import sbt._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.JsonAST.{JField, JObject}

class RequireCompilerSpec extends FunSpec {

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

    it("should parse common require.js build files to json") {

      val builds: Map[File, JObject] = (buildFiles map (a => (a, RequireCompiler.loadBuild(a)))).toMap

      assert(builds(build1) ===
        ("baseUrl" -> ".") ~
        ("modules" -> List(("name" -> "test")))
      )

      builds foreach { case (build, json) =>
        assert(json.obj.find{
          case JField("modules", _) => true
          case _ => false
        }.isDefined)
      }

    }


    it("should compile") {

      IO.delete(target)

      val builds = buildFiles map { build =>
        RequireCompiler.compile(
          sourceDir = sources,
          targetDir = target / (build.getName + "-out"),
          sourceBuild = build,
          targetBuild = target / build.getName
        )
      }

    }

  }
}

