package chatapp.server.room

import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, Props}
import akka.util.Timeout
import chatapp.server.{RedisSupportActor, client}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

object DefaultRoomActor   {
//    def props(roomName:String): Props = Props(classOf[DefaultRoomActor], roomName)
    def props(roomName:String): Props = {
        if(roomName == "globalRoom")
            Props(classOf[DefaultRoomActor], roomName)
        else
            Props(classOf[FirstGameRoomActor], roomName)
    }

    case class AddRouteeActor(actor: ActorRef, name: String)
    case class RemoveRouteeActor(actor: ActorRef, name: String)
    case object GetRoomUserCount
    case object DestroyDefaultRoomActor
}
class DefaultRoomActor(roomName:String) extends Actor with ActorLogging{
    import DefaultRoomActor._
    import client.ClientHandlerMessages._
    import client.ClientHandlerSupervisor._
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

    override def postStop(): Unit = {
        log.info(s"DefaultRoomActor postStop :: ${roomName}")
        super.postStop()
    }

    def receive :Receive = {
        case AddRouteeActor(actor, name) => {
            ActiveClients += (actor.path -> actor)

            var n = name
            if(n.length <= 0)
                n = actor.path.toStringWithoutAddress

            if(redis != null)
            {
                val set = redis.set(s"active:room:${roomName}:userlist:${n}", "")
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
                RedisSupportActor.inst ! RedisSupportActor.Publish(s"active:room:${roomName}:userlist:${clientActorName}", data)
            } catch {
                case e:Exception => e.printStackTrace
            }
        }
            
        case RemoveRouteeActor(actor, name) =>
            ActiveClients -= actor.path

            var n = name
            if(n.length <= 0)
                n = actor.path.toStringWithoutAddress
            var init = 0
            var count = 0
            var loop = true
            if(redis != null) {
              while(loop) {
                val keys = redis.scan(init,Option(100),Option(s"active:room:${roomName}:userstate:${n}:*"))
                Await.result(for {s <- keys} yield {
                  init = s.index
                  count += s.data.length
                  s.data foreach(value => {
                    val userstate = value.asInstanceOf[String]

                    val state = Await.result(
                      redis.del(s"${userstate}")
                      , timeout)
                  })
                }, timeout)
                if(init == 0)
                  loop = false
              }
              Await.result(for {
                s1 <- redis.del(s"active:room:${roomName}:userlist:${n}")
              } yield {s1}, timeout)
            }

        case GetAllClientIdentifier => {
            var init = 0
            var count = 0
            var loop = true
            var userdataTotal = scala.collection.mutable.ListBuffer[String]()
            if(redis != null) {
                while(loop) {
                    val keys = redis.scan(init,Option(100),Option(s"active:room:${roomName}:userlist:*"))
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

            if(count == 0)
                sender ! "nobody (0 users total)."
            else {
                sender ! userdataTotal.toList.reduce(_ + ", " + _)
//                log.info(userdataTotal.toString())
            }
        }

        case GetRoomUserCount => {
            var init = 0
            var count = 0
            var loop = true
            if(redis != null) {
                while(loop) {
                    val keys = redis.scan(init,Option(100),Option(s"active:room:${roomName}:userlist:*"))
                    Await.result(for {s <- keys} yield {
                        init = s.index
                        count += s.data.length
                    }, 5 seconds)
                    if(init == 0)
                        loop = false
                }
            }
            sender() ! count
        }

        case DestroyDefaultRoomActor => {
            log.info(s"DestroyDefaultRoomActor :: ${roomName}")
            ActiveClients.foreach(f => {
                context.actorSelection(f._1) ! DestroyDefaultRoomActor
            })
            ActiveClients.clear

            if(redis != null)
            {
                var init = 0
                var count = 0
                var loop = true
                if(redis != null) {
                    while(loop) {
                        val keys = redis.scan(init,Option(100),Option(s"active:room:${roomName}:*:*:*"))
                        Await.result(for {s <- keys} yield {
                            init = s.index
                            count += s.data.length
                            s.data foreach(value => {
                                val userstate = value.asInstanceOf[String]

                                val state = Await.result(
                                    redis.del(s"${userstate}")
                                    , timeout)
                            })
                        }, timeout)
                        if(init == 0)
                            loop = false
                    }

                    init = 0
                    count = 0
                    loop = true
                    while(loop) {
                      val keys = redis.scan(init,Option(100),Option(s"active:room:${roomName}:userlist:*"))
                      Await.result(for {s <- keys} yield {
                        init = s.index
                        count += s.data.length
                        s.data foreach(value => {
                          val userstate = value.asInstanceOf[String]

                          val state = Await.result(
                            redis.del(s"${userstate}")
                            , timeout)
                        })
                      }, timeout)
                      if(init == 0)
                        loop = false
                    }

                }
//                val del1 = redis.del(s"active:room:${roomName}:None")
//                val del2 = redis.del(s"active:room:${roomName}")
//                val r :Future[Long] = for {
//                    s1 <- del1
//                    s2 <- del2
//                } yield {s2}
//                Await.result(r, 5 seconds)

                log.info(s"DestroyDefaultRoomActor :: ${roomName} finish")
                sender ! ""
                RedisSupportActor.inst ! RedisSupportActor.PunSubscribe(s"active:room:${roomName}:userlist:*")
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