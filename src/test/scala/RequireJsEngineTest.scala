package net.tgambet.requirejs

import org.scalatest.FunSpec
import sbt._
import javax.script.ScriptException

class RequireJsEngineTest extends FunSpec with UseCases {

  describe("A RequireJsEngine") {

    val engine = new RequireJsEngine

    it("should run simple r.js commands without exception") {
      engine.run()
      engine.run("-v")
    }

    it("should throw an exception if r.js ends with a failure") {
      intercept[ScriptException](engine.run("-o", "404"))
    }

    it("should build a project given a valid build file") {
      withUseCase("use_case_1"){ sources =>
        engine.build(sources / "build.js")
        assert((sources / "main-min.js").exists())
      }
    }

  }

}
