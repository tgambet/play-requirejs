package org.github.tgambet

import org.scalatest.FunSpec
import java.io.File
import util.parsing.json._
import sbt.IO
import util.matching.Regex

class RequireCompilerSpec extends FunSpec {
  describe("RequireCompiler") {
    it("should parse a require.js build file as a json object") {
      val content = IO.read(new File("src/test/resources/build.js"))
      val expected = """{
        |    "appDir": "./js",
        |    "baseUrl": "./",
        |    "dir": "../../../target/test/js/",
        |    "optimize": "uglify",
        |    "modules": [{ "name": "test" }]
        |}""".stripMargin
      val jsonString = RequireCompiler.jsonify(content)
      assert(jsonString === expected)

      val json = JSON.parseRaw(jsonString).get
      assert(json.asInstanceOf[JSONObject].obj("optimize") === "uglify")
    }

    it("should compile javascripts assets using r.js compiler on the classpath and a given build file") {
      new File("target/test/js/test.js").delete()
      val buildFile = new File("src/test/resources/build.js")
      RequireCompiler.compile(buildFile)
      val compiled = IO.read(new File("target/test/js/test.js"))
      assert(compiled === """var a=[];define("test",[],function(){})""")
    }
  }
}

