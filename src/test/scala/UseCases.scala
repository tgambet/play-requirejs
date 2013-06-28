package net.tgambet

import java.io.File
import org.scalatest.Suite
import sbt._

/**
 * Created with IntelliJ IDEA.
 * User: Thomas
 * Date: 6/28/13
 * Time: 1:35 PM
 * To change this template use File | Settings | File Templates.
 */

object UseCases {

  var count = 0

}

trait UseCases { this: Suite =>

  import UseCases.count

  def withUseCase(useCase: String)(f: Function[File, Any]) = {
    count = count + 1
    val target = file("target/requirejs")
    val resources = file("src/test/resources/")
    val srcDir = resources / useCase
    val newSrcDir = target / (useCase + "-" + count)
    IO.delete(newSrcDir)
    IO.copyDirectory(srcDir, newSrcDir)
    f(newSrcDir)
    //IO.delete(newSrcDir)
  }

}
