package chatapp.server

import akka.actor._
class EchoActor extends Actor {

  def receive = {
    case msg => println(s"${self.path.name} - New msg received: $msg")
  }

}