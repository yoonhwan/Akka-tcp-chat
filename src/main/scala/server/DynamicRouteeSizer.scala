package chatapp.server
import akka.actor.{Actor, ActorRef, PoisonPill, Props, Terminated}
import akka.routing._

object DynamicRouteeSizer   {
    case class PreferredSize(size: Int)
}
class DynamicRouteeSizer(nrActors: Int, props: Props, router:ActorRef) extends Actor {
    import DynamicRouteeSizer._
    var nrChildren = nrActors
    var childInstanceNr = 0

    //restart child
    override def preStart() {
        super.preStart()
        (0 until nrChildren).map(nr=>createRoutee())
    }

    def createRoutee() {
        childInstanceNr += 1
        val child = context.actorOf(props, "routee" + childInstanceNr)
        val selection = context.actorSelection(child.path)
        router ! AddRoutee(ActorSelectionRoutee(selection))
        context.watch(child)
    }

    def receive :Receive = {
        case PreferredSize(size) => {
            if(size < nrChildren) {
                //remove routee
                context.children.take(nrChildren - size).foreach(ref => {
                    val selection = context.actorSelection(ref.path)
                    router ! RemoveRoutee(ActorSelectionRoutee(selection))
                })
                router ! GetRoutees
            }else   {
                //create routee
                (nrChildren until size).map(nr => createRoutee())
            }

            nrChildren = size
        }

        case routees: Routees => {
            // typecast Routees to actorPath
            import collection.JavaConverters._
            var active = routees.getRoutees.asScala.map{
                case x: ActorRefRoutee => x.ref.path.toString
                case x: ActorSelectionRoutee => x.selection.pathString
            }

            //process routees
            for (routee <- context.children) {
                val index = active.indexOf(routee.path.toStringWithoutAddress)
                if(index >=0) {
                    active.remove(index)
                }else {
                    //not using routee
                    routee ! PoisonPill
                }
            }

            //destroyed routee in active
            for (terminated <- active) {
                val name = terminated.substring(terminated.lastIndexOf("/")+1)
                val child = context.actorOf(props,name)
                context.watch(child)
            }
        }
        case Terminated(child) => router ! GetRoutees

    }
}