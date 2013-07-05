package net.tgambet.requirejs

import java.io.File
import org.scalatest.Suite
import sbt._

object UseCases {

  var count = 0

}

trait UseCases { this: Suite =>

  import UseCases.count

  def withUseCase[A](useCase: String)(f: Function[File, A]): A = {
    count = count + 1
    val target = file("target/requirejs")
    val resources = file("src/test/resources/")
    val srcDir = resources / useCase
    val newSrcDir = target / (useCase + "-" + count)
    IO.delete(newSrcDir)
    IO.copyDirectory(srcDir, newSrcDir)
    f(newSrcDir)
  }

}
