package org.github.tgambet

import org.scalatest.FunSpec
import java.io.File

class RequireCompilerSpec extends FunSpec {
  describe("RequireCompiler") {
    it("should copy javascript assets wihtout changing or minifying the content when used as an AssetCompiler") {
      val jsFile = new File("src/test/resources/js/test.js")
      val (full, minified, deps) = RequireCompiler.compile(jsFile, Nil)
      assert(full === "var a = [];")
      assert(minified === None)
      assert(deps.length === 0)
    }
    it("should compile javascripts assets using r.js compiler on the classpath and a given build file") {
      new File("target/test/").delete()
      val buildFile = new File("src/test/resources/build.js")
      RequireCompiler.compile(buildFile)
      val source = scala.io.Source.fromFile(new File("target/test/js/test.js"))
      val compiled = source.mkString
      source.close()
      assert(compiled === """var a=[];define("test",[],function(){})""")
    }
  }
}
