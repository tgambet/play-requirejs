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
import com.google.javascript.jscomp.{CheckLevel, JSSourceFile}
import com.google.javascript.jscomp.{Compiler, LoggerErrorManager, CheckLevel, JSError}

object RequireCompiler {

  /*def compile(
    sourceDir: Option[Path],
    targetDir: Option[Path],
    modules: Option[Set[String]],
    baseConfig: Option[File],
    cacheFile: Option[Path],
    logger: Logger) = {

    //assert baseConfig is not singleFile optimization

    val sources = ??? // sources orElse fromConfig orElse ""
    val target = ???  // targetDir orElse fromConfig orElse "build" sibling to buildFile orElse warning+./build
    val modules = ??? // either param orElse findFromCache orElse findformbaseconfig orElse warning+[]

    // construct the new build json

  }*/

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

  def parseBuildReport(report: File): Relation[Path, Path] = {
    val content = IO.read(report)
    val moduleDependencies = content.split("""\n\n""").map{case a =>
      val arr = a.split("\n----------------\n")
      if (arr.length == 2) {
        val module = Paths.get(arr(0).trim)
        val deps = arr(1).split("""\n""") map (s => Paths.get(s.trim))
        deps map ((_, module))
      } else {
        Array.empty[(Path, Path)]
      }
    }.flatten
    Relation.empty[Path, Path] ++ moduleDependencies
  }

  def getDirectories(buildFile: File): (File, File) = {
    val build = load(buildFile)
    val sources = build \ "appDir" match {
      case JString(appDir) => buildFile.toPath.getParent.resolve(appDir).toFile
      case _ => buildFile.toPath.getParent.toFile
    }
    val relOutput = {
      build \ "out" match {
        case JString(out) => Option(Paths.get(out).getParent) // getParent can return null
        case _ => None
      }
    } orElse {
      build \ "dir" match {
        case JString(dir) => Some(Paths.get(dir))
        case _ => None
      }
    } getOrElse Paths.get(".")

    val output = buildFile.toPath.getParent.resolve(relOutput).toFile
    (sources, output)
  }

  def recompile(buildFile: File, cacheFile: File): Relation[File, File] = {

    // find sourceDirectory
    val (sources, output) = getDirectories(buildFile)

    // get Last Modified info
    val assets = (sources ** "*") --- (output ** "*") filter (_.isFile)
    val currentInfos: Map[File, ModifiedFileInfo] = assets.get.map(f => f.getAbsoluteFile -> FileInfo.lastModified(f)).toMap

    // read cache to find modified files
    val (previousRelation, previousInfo) = Sync.readInfo(cacheFile)(FileInfo.lastModified.format)

//  from Play's AssetCompiler.scala
    lazy val changedFiles: Seq[File] =
      currentInfos.filter(e => !previousInfo.get(e._1).isDefined || previousInfo(e._1).lastModified < e._2.lastModified).map(_._1).toSeq ++
      previousInfo.filter(e => !currentInfos.get(e._1).isDefined).map(_._1).toSeq

    // find modules dependent on modified files
    val modulesFiles = previousRelation.filter {
      case (source, module) => {
        changedFiles.contains(sources.toPath.resolve(source.toPath).toFile)
      }
    }

    val modules = modulesFiles._2s.map(f => output.relativize(f).get.toString.replaceAll("""\.js$""", ""))

    // rebuild modules with keepBuildDir = true
    val newRels = if (previousInfo.isEmpty) {
      compile(buildFile)
    } else {
      // TODO also copy the changed files that are not modules
      compile(buildFile, modules.toList)
    }

    // update cache with new relations and info
    Sync.writeInfo(cacheFile, newRels, currentInfos)(FileInfo.lastModified.format)
    newRels
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
    println("newBuild: " + newBuild)
    val date = new SimpleDateFormat("MMMM.dd.HHmmss").format(new Date())
    val newBuildFile = buildFile.getParentFile / ("build." + date + ".js")
    IO.write(newBuildFile, pretty(render(newBuild)))
    compile(newBuildFile)
  }

  def compile(buildFile: File): Relation[File, File] = {

    println("Compiling: " + buildFile)

    val (sources, outputDir) = getDirectories(buildFile)

    val mgr: ScriptEngineManager = new ScriptEngineManager()
    val engine: ScriptEngine = mgr.getEngineByName("JavaScript")
    augmentEngine(engine)
    val stream = new java.io.InputStreamReader(this.getClass.getClassLoader.getResource("r.js").openConnection().getInputStream())
    val args = Array[String]("-o", buildFile.toString)
    engine.put("arguments", args)
    engine.put("engine", engine)
    engine.getContext.setWriter(new RequirePrintWriter)
    engine.eval(stream)

    val report = outputDir.toPath.resolve("build.txt").toFile
    report.exists() match {
      case true => Relation.empty[File, File] ++ parseBuildReport(report).all.map{
        case (a, b) => (sources.toPath.resolve(a).toFile, outputDir.toPath.resolve(b).toFile)
      }
      case _ => Relation.empty[File, File]
    }
  }

  private class RequirePrintWriter extends PrintWriter(new java.io.StringWriter()) {
    override def print(s: String) { scala.Console.println(s) }
    override def println(s: String) { print(s) }
  }

  def augmentEngine(engine: ScriptEngine) = {


    import com.google.javascript.jscomp.Compiler
    import com.google.javascript.jscomp.ErrorManager

    //com.google.javascript.jscomp.



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
    def quit(i: Int) {}
  }

}
