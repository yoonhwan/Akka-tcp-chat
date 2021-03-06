package chatapp.server

import scala.concurrent.duration._
import akka.routing._
import akka.actor._
import org.scalatest._
import akka.testkit.{ TestProbe, TestKit }

class TestActor(actor: ActorRef) extends Actor{

    def receive:Receive = {
        case _ =>{
            actor ! _
        }
    }
}
class DynamicRouteeSizerTest
  extends TestKit(ActorSystem("DynamicRouteeSizerTest"))
  with WordSpecLike with BeforeAndAfterAll {

  override def afterAll() = {
    system.terminate()
  }

  "The Router" must {
    "routes depending on speed" in {

      val endProbe = TestProbe()
      val router = system.actorOf(RoundRobinGroup(List()).props(), "router")
        val props = Props(new TestActor(endProbe.ref))
        val creator = system.actorOf(Props( new DynamicRouteeSizer(2, props, router)), "DynamicRouteeSizer")



    }
  }
}