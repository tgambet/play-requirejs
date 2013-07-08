package net.tgambet.requirejs

import org.scalatest.FunSpec
import sbt._

class RequireJsEngineTest extends FunSpec with UseCases {

  describe("A RequireJsEngine") {

    val engine = new RequireJsEngine

    it("should run r.js with no arguments without exception") {
      engine.run(Array.empty)
    }

    it("should throw an exception if r.js throws an exception") {
      intercept[Exception](engine.run(Array("-o", "404")))
    }

    it("should build a project given a valid build file") {
      withUseCase("use_case_1"){ sources =>
        engine.build(sources / "build.js")
        assert((sources / "main-min.js").exists())
      }
    }

  }

}
