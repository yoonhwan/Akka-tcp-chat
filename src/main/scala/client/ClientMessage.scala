package chatapp.client

/**
  * Created by yoonhwan on 02-4-2018
  */
object ClientMessage {

  case class SendMessage(message: String)
  case object ClientConnected
  case class ClientError(error: String)
}
