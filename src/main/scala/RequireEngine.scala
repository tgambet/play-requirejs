package net.tgambet

import java.io.File
import sbt.{Level, Logger, IO}
import javax.script._

object RequireEngine {

  val defaultLogger = new Logger {
    def log(level: Level.Value, message: => String) { scala.Console.println("[r.js] " + message) }
    def success(message: => String) {}
    def trace(t: => Throwable) {}
  }

}

class RequireEngine(val logger: Logger = RequireEngine.defaultLogger) {

  val engine: ScriptEngine = {
    val engine = new ScriptEngineManager().getEngineByName("JavaScript")
    engine.getContext.setErrorWriter(WriterToLogger)
    engine.getContext.setWriter(WriterToLogger)

    val globals = engine.createBindings()
    globals.put("engine", engine)

    val engineFix = new Object {
      def load(file: String) = engine.eval(readFile(file))
      def readFile(fileName: String): String = IO.read(new File(fileName))
      def quit(code: Int) {}
    }
    globals.put("engineFix", engineFix)

    val jLogger = new Object {
      def info(s: String)  { s.split("\n").foreach( s => logger.info(s) ) }
      def warn(s: String)  { s.split("\n").foreach( s => logger.warn(s) ) }
      def error(s: String) { s.split("\n").foreach( s => logger.error(s) ) }
      def trace(s: String) { s.split("\n").foreach( s => logger.debug(s) ) }
    }
    globals.put("jlogger", jLogger)

    engine.setBindings(globals, ScriptContext.GLOBAL_SCOPE)
    engine
  }

  lazy val rjs = {
    val stream = this.getClass.getClassLoader.getResource("r-mod.js").openConnection().getInputStream()
    val rjs = IO.readStream(stream)
    engine match {
      case engine: Compilable => engine.compile(addFixesToScope + rjs)
      case _ => sys.error("Engine is not a Compilable")
    }
  }

  def run(args: Array[String]) {
    val bindings = engine.createBindings()
    bindings.put("arguments", args)
    rjs.eval(bindings)
  }

  def build(buildFile: File) { run(Array[String]("-o", buildFile.toString)) }

  val addFixesToScope =
    """
      |for(var fn in <name>) {
      |  if(typeof <name>[fn] === 'function') {
      |    this[fn] = (function() {
      |      var method = <name>[fn];
      |      return function() {
      |        return method.apply(<name>, arguments);
      |      };
      |    })();
      |  }
      |};
    """.stripMargin.replaceAll("<name>", "engineFix").replaceAll("\n"," ")

  object WriterToLogger extends java.io.PrintWriter(new java.io.StringWriter()) {
    override def print(s: String) { logger.info(s) }
    override def println(s: String) { print(s) }
  }

}
