package net.tgambet.requirejs

import sbt.IO
import sbt.Logger
import java.io.File
import javax.script._

/**
 * A RequireJS engine is an interface to require.js optimizer, r.js. The optimizer is run as a pre-compiled script
 * through a javax.script.Engine, resulting in exceptions being adequately thrown when the build fails.
 * The internal logger of r.js is mapped to an sbt logger which by default prints on the console messages of level info
 * and above.
 */
class RequireJsEngine {

  /**
   * A javax.script.ScriptEngine configured to be compatible with r.js. Specifically it completes the global scope with
   * methods that are expected by r.js when running in a Rhino shell. It also references itself as the global "engine"
   * variable for use by r.js.
   */
  val engine: ScriptEngine = {
    val engine = new ScriptEngineManager().getEngineByName("JavaScript")
    val globals = engine.createBindings()

    // make the engine itself available in the scope
    globals.put("engine", engine)

    // Missing rhino methods
    // https://developer.mozilla.org/en/docs/Rhino/Shell#readFile.28path_.5B.2C_characterCoding.29
    // https://developer.mozilla.org/en/docs/Rhino/Shell#load.28.5Bfilename.2C_....5D.29
    val engineFix = new Object {
      def load(fileName: String) = engine.eval(IO.read(new File(fileName)))
      def readFile(fileName: String) = IO.read(new File(fileName))
      def quit(code: Int) {}
    }
    globals.put("engineFix", engineFix)

    engine.setBindings(globals, ScriptContext.GLOBAL_SCOPE)
    engine
  }

  /**
   * The compiled optimizer script. Some modifications were made to the source of r.js:
   * - wait for the build promise to complete and throw an exception if failed. By default it only logs on the console.
   * - use a logger present in the scope instead of indiscriminately calling print() for all levels.
   * - use the engine passed in the scope instead of loading a rhino engine.
   */
  val rjs = {
    // Copy the method of the engineFix object to the root scope
    val addMethodsToScope =
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

    // Converts the arguments parameter from an Array[Object] to an Array[String].
    val toStringArray = "var r = []; for (i in arguments) { r.push(String(arguments[i])); } arguments = r; delete r;"

    val stream = this.getClass.getClassLoader.getResource("r-2.1.8.js").openConnection().getInputStream()
    val rjs = IO.readStream(stream)

    engine match {
      case engine: Compilable => engine.compile(addMethodsToScope + toStringArray + rjs)
      case _ => sys.error("Engine is not a Compilable")
    }
  }

  /**
   * Runs r.js compiler with the specified command-line arguments.
   * @param args arguments to pass to r.js
   */
  def run(args: Array[String], logger: Logger = SystemLogger) {
    logger.debug("Calling r.js with arguments: " + args.toArray.mkString(" "))
    val bindings = engine.createBindings()
    bindings.put("arguments", args)

    // The javascript logger mapped to ours. `trace` is used as `debug` by r.js.
    val jLogger = new Object {
      def info(s: String)  { s.split("\n").foreach( s => logger.info(s.trim) ) }
      def warn(s: String)  { logger.warn(s.trim) }
      def error(s: String) { logger.error(s.trim) }
      def trace(s: String) { logger.debug(s.trim) }
    }
    bindings.put("jLogger", jLogger)

    val console = new Object {
      def log(msg: String) { logger.info(msg) }
    }
    bindings.put("console", console)

    rjs.eval(bindings)
  }

  def run(args: String*) { run(args.toArray) }

  /**
   * Call r.js only passing a build file. Equivalent to calling run("-o", buildFile.toString).
   * @param buildFile a require.js build file
   */
  def build(buildFile: File, logger: Logger = SystemLogger) { run(Array("-o", buildFile.toString), logger) }


}
