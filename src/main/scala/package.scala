package net.tgambet

import java.io.File
import sbt.{Logger, Level}

package object requirejs {

  object FileImplicits {
    def file(s: String) = new File(s)
    case class FileW(file: File) {
      def / (other: File): File = file.toPath.resolve(other.toPath).toFile
      def / (other: String): File = file.toPath.resolve(other).toFile
      def parent: File = {
        if (file.getParentFile != null) {
          file.getParentFile
        } else new File("")
      }
      def normalize() = file.toPath.normalize.toFile
      def isChildOf(other: File) = file.toPath.startsWith(other.toPath)
      def relativeTo(other: File): File =
        other.toPath.toAbsolutePath.relativize(file.toPath.toAbsolutePath).toFile
    }
    implicit def toFileW(file: File): FileW = FileW(file)
  }

  object SystemLogger extends Logger {
    def log(level: Level.Value, message: => String) {
      if (level >= Level.Info)
        Console.out.println("[" + level.toString.toLowerCase + "] " + message)
    }
    def success(message: => String) {}
    def trace(t: => Throwable) {}
  }

}