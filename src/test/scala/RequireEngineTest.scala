package net.tgambet

import org.scalatest.FunSpec
import sbt._

class RequireEngineTest extends FunSpec with UseCases {

  describe("A RequireEngine") {

    val engine = new RequireEngine()

    it("should run r.js with no arguments without exception") {
      engine.build(Array.empty[String])
    }

    it("should throw an exception if r.js throws an exception") {
      intercept[Exception](engine.build(Array("-o", "404")))
    }

    it("should build a project given a valid build file") {
      withUseCase("use_case_1"){ sources =>
        engine.build(sources / "build.js")
        assert((sources / "main-min.js").exists())
      }
    }

  }

}
