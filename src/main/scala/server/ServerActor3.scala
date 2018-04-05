// package chatapp.server

// import java.net.InetSocketAddress

// import akka.actor.{Actor, ActorRef, Props, ActorLogging, ActorSystem}
// import akka.io.Tcp._
// import akka.io.{IO, Tcp}
// import akka.util.ByteString

// /**
//   * Created by Niels Bokmans on 30-3-2016.
//   */

// case class SendMessage(clientActorName: String, clientIdentify: String, message: String, serverMessage: Boolean = false)
// case class Identify(clientActorName: String)

// class ServerActor(actorSystem: ActorSystem) extends Actor {
//   val Port = 18573
//   val Server = "localhost"
//   val ActiveClients = scala.collection.mutable.HashMap.empty[String, ActorRef]
  
//   IO(Tcp)(actorSystem) ! Bind(self, new InetSocketAddress(Server, Port))
//   // SO.TcpNoDelay(false)
//   def receive: Receive = {

//     case CommandFailed(_: Bind) =>
//       println("Failed to start listening on " + Server + ":" + Port)
//       context stop self
//       actorSystem.terminate()

//     case Bound(localAddress: InetSocketAddress) =>
//       println("Started listening on " + localAddress)

//     case Connected(remote, local) =>
//       val handler = context.actorOf(EchoHandler.props(self, sender(), remote))
//       sender() ! Register(handler, keepOpenOnPeerClosed = true)
//       ActiveClients += (sender.path.name -> handler)

//     case SendMessage(clientActorName, clientIdentify, message, serverMessage) =>
//       val text = data.decodeString("UTF-8")
//       if (isCommand(text)) {
//         getCommand(text) match {
//           // case "quit" => quit(clientActorName)
//           // case "identify" => identify(clientActorName, text)
//           // case "online" => online(clientActorName)
//           // case _ => sendMessage(clientActorName, clientIdentify, "Unknown command!", serverMessage = true)
//         }
//       } else {
//         if (clientIdentify.length == 0) {
//           sendMessage(clientActorName, clientIdentify, "Please identify yourself using ~identify [name]!", serverMessage = true)
//         } else {
//           sendToAll(clientIdentify, text)
//         }
//       }
//   }

//   def getActorRefByName(name: String): ActorRef = {
//       ActiveClients.get(name).get
//     }

//   def quit(clientActorName: String, identify: String): Unit = {
//       if (ActiveClients.contains(clientActorName)) {
//         ActiveClients -= clientActorName
//         getActorRefByName(clientActorName) send stop
      
//         sendToAll("", "<" + ClientIdentities.get(clientActorName).get + "> has left the chatroom.", serverMessage = true)
//         ClientIdentities -= clientActorName
//       }
//     }
//   def isCommand(message: String): Boolean = {
//       message.startsWith(CommandCharacter)
//     }

//   def online(clientActorName: String): Unit = {
//     sendMessage(clientActorName, "Currently active users: " + activeUsers, serverMessage = true)
//   }

//   def activeUsers: String = {
//     if (ClientIdentities.isEmpty) "nobody (0 users total)."
//     else ClientIdentities.values.reduce(_ + ", " + _) +
//       " (" + ClientIdentities.size + " users total)."
//   }

//   def sendMessage(clientActorName: String, clientIdentify: String, message: String, serverMessage: Boolean = false) = {
//     val actorRef = getActorRefByName(clientActorName)
//     actorRef ! SendMessage(clientActorName, clientIdentify, message, serverMessage)
//   }
  
//   def sendToAll(clientIdentify: String, message: String, serverMessage: Boolean = false) = {
//     if (serverMessage) {
//       ActiveClients.foreach(f => sendMessage(f._1, message, serverMessage))
//     } else {
//       ActiveClients.foreach(f => sendMessage(f._1, "<" + clientIdentify + ">: " + message, serverMessage))
//     }
//   }

//   // def getRest(message: String): String = {
//   //   val command = getCommand(message)
//   //   message.substring(command.length() + 1, message.length())
//   // }

//   def getCommand(message: String): String = {
//     val split = message.split(" ")
//     val command = split(0)
//     val actualCommand = command.substring(1, command.length())
//     actualCommand
//   }
// }

// object EchoHandler {

//   def props(server: ActorRef, connection: ActorRef, remote: InetSocketAddress): Props =
//     Props(classOf[EchoHandler], connection, remote)

// }

// class EchoHandler(server: ActorRef, connection: ActorRef, remote: InetSocketAddress)
//   extends Actor with ActorLogging {

//     import Tcp._
//     import EchoHandler._
//     val CommandCharacter = "~"
//     val Identify = ""

//     send("Please identify yourself using ~identify [name]!", serverMessage = true)
      
//     // sign death pact: this actor terminates when connection breaks
//     context watch connection

//     // start out in optimistic write-through mode
//     def receive = writing

//     def writing: Receive = {   
//       case PeerClosed     => 
//         println("PeerClosed")
//         // val clientActorName = path.name
//         // quit(clientActorName)
//       case Received(data) =>
//         println("Received : " + data)
//         val text = data.decodeString("UTF-8")
//         val clientActorName = self.path.name
//         if (isCommand(text)) {
//           getCommand(text) match {
//             case "quit" => quit(clientActorName)
//             case "identify" => identify(clientActorName, text)
//             case "online" => online(clientActorName)
//             case _ => sendMessage(clientActorName, "Unknown command!", serverMessage = true)
//           }
//         } else {
//           if (!ClientIdentities.contains(clientActorName)) {
//             sendMessage(clientActorName, "Please identify yourself using ~identify [name]!", serverMessage = true)
//           } else {
//             sendToAll(ClientIdentities.get(clientActorName).get, text)
//           }
//         }
//       case SendMessage(clientActorName, message, serverMessage) =>
//         send(message, serverMessage)
//     }

//     def sendMessage(message: String, serverMessage: Boolean = false) = {
//       server ! SendMessage(self.path.name, message, serverMessage)
//     }

//     def send(message: String, serverMessage: Boolean = false) = {
//       if (serverMessage) {
//         self ! Write(ByteString("[SERVER]: " + message))
//       } else {
//         self ! Write(ByteString(message))
//       }
//     }
//   }
  