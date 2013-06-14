package org.github.tgambet

import java.io._

object RequireCompiler {

  def jsonify(content: String) = {
    val comments = """(/\*.*?\*/)|(^\s*//.*$)""".r
    val blanks = """^\s*\n""".r
    val keys = """(\w+):""".r
    val parens = """(?s)\A\((.*)\)\z""".r

    var r = content
    r = comments.replaceAllIn(r, "").trim
    r = blanks.replaceAllIn(r, "").trim
    r = keys.replaceAllIn(r, "\"$1\":")
    r = parens.replaceFirstIn(r, "$1")
    r
  }

  def compile(buildFile: File): Unit = {
    import org.mozilla.javascript._
    import org.mozilla.javascript.tools.shell._

    val ctx = {
      val c = Context.enter
      c.setOptimizationLevel(-1)
      c
    }
    val scope = {
      val global = new Global
      global.init(ctx)
      ctx.initStandardObjects(global)
    }
    try {
      val args = ctx.newArray(scope, Array[Object]("-o", buildFile.getAbsolutePath))
      scope.put("arguments", scope, args)
      val ir = new java.io.InputStreamReader(this.getClass.getClassLoader.getResource("r.js").openConnection().getInputStream())
      ctx.evaluateReader(scope, ir, "r.js", 1, null)
    } catch { case e: Exception =>
      throw e
    } finally {
      Context.exit()
    }
  }
}
