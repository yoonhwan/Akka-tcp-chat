package chatapp.server

import java.net.InetSocketAddress

import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy._
import akka.actor.{Actor, ActorRef, Props, ActorLogging, ActorSystem, Terminated}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import akka.util.ByteString

class ServerActor(actorSystem: ActorSystem) extends Actor with ActorLogging{
    val Port = 18573
    val Server = "localhost"
    val supervisor = context.actorOf(ClientHandlerSupervisor.props(), "client-handler-supervisor")
      
    IO(Tcp)(actorSystem) ! Bind(self, new InetSocketAddress(Server, Port))
    // SO.TcpNoDelay(false)
    def receive: Receive = {

    case CommandFailed(_: Bind) =>
      log.info("Failed to start listening on " + Server + ":" + Port)
      context stop self
      actorSystem.terminate()

    case Bound(localAddress: InetSocketAddress) =>
      log.info("Started listening on " + localAddress)

    case Connected(remote, local) =>
      log.info(s"Tcp Server Connected. remote=<$remote>, local=<$local>. Registering handler...")
      supervisor ! ClientHandlerActor.props(supervisor, sender(), remote)
      context watch supervisor

    case _: ConnectionClosed =>
      log.info("connection closed")
    case obj: Terminated => 
      log.info(obj + " : Terminated")
    case _: Unbound =>
      log.info("connection Unbound")
  }
}
