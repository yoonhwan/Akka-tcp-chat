package chatapp.client

/**
  * Created by yoonhwan on 02-4-2018
  */

object SERIALIZER extends Enumeration {
  val ROW = Value(0)        // for command line
  val JSON= Value(1)        // json
  val ZEROF = Value(2)      // zeroformatter client only

  def withNameOpt(key: Int): Option[Value] = values.find(_ == key)
}

object ClientMessage {

  case class SendMessage(message: String)
  case object ClientConnected
  case class ClientError(error: String)
}
