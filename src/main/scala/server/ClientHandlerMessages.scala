package chatapp.server

import chatapp.client.SERIALIZER

/**
  * Created by yoonhwan on 02-4-2018
  */
object ClientHandlerMessages {
    case class SendServerMessage(message: String)
    case class SendAllClientMessage(serializer:SERIALIZER.Value, clientActorName:String, message: String)
    case class SendRoomClientMessage(serializer:SERIALIZER.Value, roomName:String, clientActorName:String, message: String)
    
    case class SendErrorMessage(error: String)
}
