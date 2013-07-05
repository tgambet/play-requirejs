package net.tgambet.requirejs

class RequireJsException(message: String = null, cause: Throwable = null) extends Exception(message, cause)

object RequireJsException {
  def apply(message: String = null, cause: Throwable = null) = new RequireJsException(message, cause)
}
