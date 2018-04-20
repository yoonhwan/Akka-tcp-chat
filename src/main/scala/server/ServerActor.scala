package chatapp.server

import java.net.{InetAddress, InetSocketAddress}

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Terminated}
import akka.io.Tcp._
import akka.io._

class ServerActor(actorSystem: ActorSystem) extends Actor with ActorLogging{
    import ClientHandlerMessages._
    val Port:Int = actorSystem.settings.config.getInt("akka.server.port")
    val Server:String = actorSystem.settings.config.getString("akka.server.hostname")
    val supervisor = context.actorOf(ClientHandlerSupervisor.props(), "client-handler-supervisor")
      
    IO(Tcp)(actorSystem) ! Bind(self, new InetSocketAddress(InetAddress.getByName(Server), Port))
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
    case Terminated(obj) => 
      log.info(obj + " : Terminated")
    case _: Unbound =>
      log.info("connection Unbound")
    
    case SendServerMessage(message) =>
      supervisor ! SendServerMessage(message)

    case ClientHandlerSupervisor.ClearAllChatRoom =>
      supervisor ! ClientHandlerSupervisor.ClearAllChatRoom
  }
}

class ServerActorUDP(actorSystem: ActorSystem) extends  Actor with ActorLogging{
  val Port:Int = actorSystem.settings.config.getInt("akka.server.port-udp")
  val Server:String = actorSystem.settings.config.getString("akka.server.hostname")
  val supervisor = context.actorOf(ClientHandlerSupervisor.props(), "client-handler-supervisor")

  IO(Udp)(actorSystem) ! Udp.Bind(self, new InetSocketAddress(InetAddress.getByName(Server), Port))
  def receive = {
    case Udp.Bound(local) ⇒
      log.info("Started listening on " + local)
      context.become(ready(sender()))
  }

  def ready(socket: ActorRef): Receive = {
    case Udp.Received(data, remote) ⇒
      log.info(s"Received from ${remote}")
      log.info(s"Response Received ${data}")
      socket ! Udp.Send(data, remote) // example server echoes back

    case Udp.Unbind  ⇒ socket ! Udp.Unbind
    case Udp.Unbound ⇒ context.stop(self)
    case Udp.CommandFailed(_: Command) =>
      log.info("Failed to start listening on " + Server + ":" + Port)
      context stop self
      actorSystem.terminate()
    case Terminated(obj) =>
      log.info(obj + " : Terminated")
  }
}