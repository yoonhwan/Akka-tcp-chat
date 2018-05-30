package chatapp.server
import scala.util.{Failure, Success}
import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, Props}
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

object DefaultRoomActor   {
    def props(roomName:String): Props = Props(classOf[DefaultRoomActor], roomName)

    case class AddRouteeActor(actor: ActorRef, name: String)
    case class RemoveRouteeActor(actor: ActorRef, name: String)
    case object GetRoomUserCount
    case object DestroyDefaultRoomActor
}
class DefaultRoomActor(roomName:String) extends Actor with ActorLogging{
    import ClientHandlerMessages._
    import ClientHandlerSupervisor._
    import DefaultRoomActor._
    val timeout = 5 seconds
    implicit val t = Timeout(timeout)
    val remoteConfig = context.system.settings.config.getConfig("akka").getConfig("remote").getConfig("netty.tcp")
    val actorHost = remoteConfig.getString("hostname")
    val actorPort = remoteConfig.getInt("port")
    val workerName = self.path.name
    val actorPath = "akka.tcp://" + context.system.name + "@" + actorHost + ":" + actorPort + "/user/" + workerName

    val ActiveClients = scala.collection.mutable.HashMap.empty[ActorPath, ActorRef]
    val redis = RedisSupportActor.redis.getOrElse(null)
    if(redis != null)
    {
        val set = redis.set(s"active:room:${roomName}:None", "")
        val r :Future[Boolean] = for {
            s1 <- set
        } yield {s1}
        r.onComplete {
          case Success(s)=>
          case Failure(e) => e.printStackTrace
        }
        Await.result(r, timeout)
        RedisSupportActor.inst ! RedisSupportActor.PSubscribe(onPMessage, s"active:room:${roomName}:*")
    }

    //restart child
    override def preStart() {
        super.preStart()
        ActiveClients.clear
    }

    def receive :Receive = {
        case AddRouteeActor(actor, name) => {
            ActiveClients += (actor.path -> actor)

            var n = name
            if(n.length <= 0)
                n = actor.path.toStringWithoutAddress

            if(redis != null)
            {
                val set = redis.set(s"active:room:${roomName}:${n}", "")
                val r :Future[Boolean] = for {
                    s1 <- set
                } yield {s1}

              r.onComplete {
                case Success(s)=>
                case Failure(e) => e.printStackTrace
              }
                Await.result(r, 5 seconds)
            }
        }
        case server @ SendServerMessage(message) => {
            try {
                ActiveClients.foreach(f => {
                    context.actorSelection(f._1) ! server
                })

                val data = s"${actorPath }|${chatapp.client.SERIALIZER.ROW.id}|server|${message}"
                RedisSupportActor.inst ! RedisSupportActor.Publish(s"active:room:globalRoom:server", data)
            } catch {
                case e:Exception => e.printStackTrace
            }
        }

        case all @ (clientActorName, message) => {
            try {
                ActiveClients.foreach(f => {
                    context.actorSelection(f._1) ! all
                })

                val data = s"${actorPath }|${chatapp.client.SERIALIZER.ROW.id}|${clientActorName}|${message}"
                RedisSupportActor.inst ! RedisSupportActor.Publish(s"active:room:globalRoom:server", data)
            } catch {
                case e:Exception => e.printStackTrace
            }
        }
        case m @ SendRoomClientMessage(serializer, roomName, clientActorName, message) => {
            try {
                ActiveClients.foreach(f => {
                    context.actorSelection(f._1) ! m
                })

                val data = s"${actorPath }|${serializer.id}|${clientActorName}|${message}"
                RedisSupportActor.inst ! RedisSupportActor.Publish(s"active:room:${roomName}:${clientActorName}", data)
            } catch {
                case e:Exception => e.printStackTrace
            }
        }
            
        case RemoveRouteeActor(actor, name) =>
            ActiveClients -= actor.path

            var n = name
            if(n.length <= 0)
                n = actor.path.toStringWithoutAddress
            if(redis != null)
            {
                val del = redis.del(s"active:room:${roomName}:${n}")
                val r :Future[Long] = for {
                    s1 <- del
                } yield {s1}
                Await.result(r, 5 seconds)
            }

        case GetAllClientIdentifier => {
            if (ActiveClients.isEmpty) 
                sender ! "nobody (0 users total)."
            else {
                var init = 0
                var count = 0
                var loop = true
                var userdataTotal = scala.collection.mutable.ListBuffer[String]()
                if(redis != null) {
                    while(loop) {
                        val keys = redis.scan(init,Option(100),Option(s"active:room:${roomName}:*"))
                        Await.result(for {s <- keys} yield {
                            init = s.index
                            count += s.data.length
                            s.data foreach(value => {
                                val user = value.asInstanceOf[String]
                                userdataTotal += user
                            })
                        }, 5 seconds)
                        if(init == 0)
                            loop = false
                    }
                }
                sender ! userdataTotal.toList.reduce(_ + ", " + _)

                log.info(userdataTotal.toString())
            }
        }

        case GetRoomUserCount => {
            sender() ! ActiveClients.size
        }

        case DestroyDefaultRoomActor => {
            ActiveClients.foreach(f => {
                context.actorSelection(f._1) ! DestroyDefaultRoomActor
            })
            ActiveClients.clear
            sender ! ""

            if(redis != null)
            {
                val del = redis.del(s"active:room:${roomName}")
                val r :Future[Long] = for {
                    s1 <- del
                } yield {s1}
                Await.result(r, 5 seconds)

                RedisSupportActor.inst ! RedisSupportActor.PunSubscribe(s"active:room:${roomName}:*")
            }
        }
    }

    def onPMessage(pmessage: String) {
        var serial = chatapp.client.SERIALIZER.ROW
        var senderPath = ""
        var username = "test"
        var message= pmessage
        if(pmessage.contains('|') == true) {
          val data = pmessage.split('|')

          if(data(0)!=actorPath)
            senderPath = data(0)

          try {
            serial = chatapp.client.SERIALIZER.withNameOpt(data(1).toInt) getOrElse chatapp.client.SERIALIZER.ROW
          } catch {
            case e:Exception => {
              e.printStackTrace
              null
            }
          }
          username = data(2)
          message = data(3)
        }

        if(senderPath != "") {
          ActiveClients.foreach(f => {
            context.actorSelection(f._1) ! SendRoomClientMessage(serial, roomName, username, message)
          })
        }
    }

}