package net.tgambet

import org.scalatest.FunSpec
import sbt._

class RequireEngineTest extends FunSpec {

  def withUseCase(useCase: String)(f: Function[File, Any]) = {
    val target = new File("target/requirejs")
    val resources = new File("src/test/resources/")
    val srcDir = resources / useCase
    val newSrcDir = target / useCase
    IO.delete(newSrcDir)
    IO.copyDirectory(srcDir, newSrcDir)
    f(newSrcDir)
  }

  describe("A RequireEngine") {

    val engine = new RequireEngine()

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
