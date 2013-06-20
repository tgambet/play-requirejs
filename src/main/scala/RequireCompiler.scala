package org.github.tgambet

import sbt._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.mozilla.javascript._
import org.mozilla.javascript.tools.shell._
import util.matching.Regex
import java.nio.file.{Path, Paths}
import javax.script._
import java.io.PrintWriter
import java.util.Date
import java.text.SimpleDateFormat

object RequireCompiler {

  private def jsonify(jsContent: String): String = {
    val replacements: Seq[(Regex, String)] = Seq(
      ("""(//.*)""".r -> ""),             // remove single-line comments
      ("""(?s)(/\*.*?\*/)""".r -> ""),    // remove multi-line comments
      ("""(?m)^\s*\n""".r -> ""),         // remove empty lines
      ("""'""".r -> "\""),                // single to double quotes
      ("""(\w+):""".r -> "\"$1\":"),      // quote unquoted keys
      ("""(?s)\A\((.*)\)\z""".r -> "$1")  // remove wrapping parenthesis
    )
    replacements.foldLeft(jsContent){ case (content, (regex, replace)) =>
      regex.replaceAllIn(content.trim, replace)
    }
  }

  def load(build: File): JObject = {
    try {
      val content = IO.read(build)
      parse(if (build.getName.endsWith(".js")) {jsonify(content)} else {content}).asInstanceOf[JObject]
    } catch {
      case e: Exception => throw new Exception("Error loading buildFile: " + build, e)
    }
  }

  /*def writeBuild(config: Config, target: File = new File("build_managed.js")): File = {

    val paths = Seq(
      "dir",
      "out",
      "appDir",
      "wrap.endFile",
      "wrap.startFile",
      "mainConfigFile"
    )

    def relativize(json: JObject): JObject = {
      json.transformField{
        case JField(key, JString(path)) if paths.contains(key) => (key, path)
        case JField(key, obj: JObject) => (key, relativize(obj))
      }.asInstanceOf[JObject]
    }



    for {
      JField("appDir", JString(appDir)) <- config
      JField("dir", JString(dir)) <- config
      JField("mainConfigFile", JString(mainConfigFile)) <- config
      JField("out",            JString(out)) <- config
      JField("wrap.endFile",   JString(endFile)) <- config
      JField("wrap.startFile", JString(startFile)) <- config
    } yield age

    val appDir = new File((config \ "appDir").asInstanceOf[JString])
  }*/

  /*  def createBuildFile(sourceDir: File, targetDir: File, sourceBuild: File, targetBuild: File = new File("build_managed.js")): File = {
    val sourceJson = load(sourceBuild)
    val dir = targetBuild.toPath.getParent.relativize(targetDir.toPath).toString
    val appDir = targetBuild.toPath.getParent.relativize(sourceDir.toPath).toString
    val defaults = ("optimize" -> "none") ~ ("baseUrl" -> ".")
    val overrides = ("dir" -> dir) ~ ("appDir" -> appDir)
    val targetJson = overrides merge sourceJson merge defaults
    IO.write(targetBuild, pretty(render(targetJson)))
    targetBuild
  }*/

  /*  def compile(sourceDir: File, targetDir: File, sourceBuild: File, targetBuild: File = new File("build_managed.js")): Relation[File, File] = {
    compile(createBuildFile(sourceDir, targetDir, sourceBuild, targetBuild))
    val buildTxt = targetDir / "build.txt"
    val content = IO.read(buildTxt)
    val moduleDependencies: Seq[(File, File)] = content.split("""\n\n""").map{case a =>
      val arr = a.split("\n----------------\n")
      val module = new File(arr(0).trim)
      val deps = arr(1).split("""\n""") map (s => new File(s.trim))
      deps map ((_, module))
    }.flatten.toSeq
    Relation.empty[File, File] ++ moduleDependencies
  }*/

  def parseBuildReport(report: File): Relation[File, File] = {
    val content = IO.read(report)
    val moduleDependencies: Seq[(File, File)] = content.split("""\n\n""").map{case a =>
      val arr = a.split("\n----------------\n")
      val module = new File(arr(0).trim)
      val deps = arr(1).split("""\n""") map (s => new File(s.trim))
      deps map ((_, module))
    }.flatten.toSeq
    Relation.empty[File, File] ++ moduleDependencies
  }

  def compile(buildFile: File, modules: List[String]): Relation[File, File] = {
    val build = load(buildFile)
    val newBuild = build.transformField {
      case ("modules", base: JArray) => {
        val filtered = base.filter {
          case p: JObject =>  modules.contains(p.values("name").asInstanceOf[String])
          case _ => false
        }
        ("modules", filtered)
      }
    }
    val date = new SimpleDateFormat("MMMM.dd.HHmmss").format(new Date())
    val newBuildFile = buildFile.getParentFile / ("build." + date + ".js")
    IO.write(newBuildFile, pretty(render(newBuild)))
    compile(newBuildFile)
  }

  def compile(buildFile: File): Relation[File, File] = {

    println("Compiling: " + buildFile)


    val build = load(buildFile).values
    val outputDir: Path = {
      build get("out") map { case out: String =>
        val path = Paths.get(out)
        if (path.getParent != null) path.getParent
        else Paths.get("")
      }
    } orElse {
      build get("dir") map { case dir: String =>
        Paths.get(dir)
      }
    } getOrElse {
      throw new Exception(""" "dir" or "out" options not specified""")
    }

    /*if (track) {
      val cacheFile = buildFile.getParentFile / "build.cache"
      val assets = (resources / "js" ** "*")
      val currentInfos: Map[File, ModifiedFileInfo] = assets.get.map(f => f.getAbsoluteFile -> FileInfo.lastModified(f)).toMap
    }*/

    val mgr: ScriptEngineManager = new ScriptEngineManager()
    val engine: ScriptEngine = mgr.getEngineByName("JavaScript")
    augmentEngine(engine)
    val stream = new java.io.InputStreamReader(this.getClass.getClassLoader.getResource("r.js").openConnection().getInputStream())
    val args = Array[String]("-o", buildFile.toString)
    engine.put("arguments", args)
    engine.put("engine", engine)
    engine.getContext.setWriter(new RequirePrintWriter)
    engine.eval(stream)

    val report = buildFile.toPath.getParent.resolve(outputDir).resolve("build.txt").toFile
    report.exists() match {
      case true => parseBuildReport(report)
      case _ => Relation.empty[File, File]
    }
  }

  /*private def call(buildFile: File) = {
    ContextFactory.getGlobal.call(new ContextAction {
      def run(ctx: Context): AnyRef = {
        val scope: Scriptable = {
          val global = new Global
          global.init(ctx)
          ctx.initStandardObjects(global)
        }
        ctx.setErrorReporter(new ErrorReporter {
          def warning(message: String, sourceName: String, line: Int, lineSource: String, lineOffset: Int) {}
          def error(message: String, sourceName: String, line: Int, lineSource: String, lineOffset: Int) {}
          def runtimeError(message: String, sourceName: String, line: Int, lineSource: String, lineOffset: Int): EvaluatorException = null
        })
        val stream = new java.io.InputStreamReader(this.getClass.getClassLoader.getResource("r.js").openConnection().getInputStream())
        val args = ctx.newArray(scope, Array[Object]("-o", buildFile.getAbsolutePath))
        scope.put("arguments", scope, args)
        ctx.evaluateReader(scope, stream, "r.js", 1, null)
      }
    })
  }*/

  private class RequirePrintWriter extends PrintWriter(new java.io.StringWriter()) {
    override def print(s: String) { scala.Console.println(s) }
    override def println(s: String) { print(s) }
  }

  def augmentEngine(engine: ScriptEngine) = {
    engine.getBindings(ScriptContext.GLOBAL_SCOPE).put("utils", new Utils(engine))
    // TODO implement a js Logger
    engine.eval(
      """
        |for(var fn in utils) {
        |  if(typeof utils[fn] === 'function') {
        |    this[fn] = (function() {
        |      var method = utils[fn];
        |      return function() {
        |        return method.apply(utils, arguments);
        |      };
        |    })();
        |  }
        |}
      """.stripMargin
    )
  }

  private class Utils(engine: ScriptEngine) {
    def readFile(fileName: String): String = {
      IO.read(new File(fileName))
    }
    def load(file: String) = {
      engine.eval(readFile(file))
    }
  }

}
