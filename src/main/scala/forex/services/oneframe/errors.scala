package forex.services.oneframe

object errors {

  sealed abstract class Error(message: String) extends Exception(message)

  object Error {

    final case class OneFrameConnectionError(message: String) extends Error(message)

    final case class OneFrameSystemError(message: String) extends Error(message)

  }

}
