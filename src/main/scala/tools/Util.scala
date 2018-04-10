package chatapp.server
import scala.collection.mutable.{HashMap,MapBuilder}
import scala.concurrent.{Await,Future,Promise}
import scala.util.{Success,Failure}
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global

object Util {
    def sequenceMap[A, B](in: HashMap[B, Future[A]]): Future[Map[B, A]] = {
        Future.sequence { in.map { case (i, f) => f.map((i, _))} }.map(_.toMap)
    }

    def sequenceList(in: List[Future[Any]]): Future[List[Any]] = {
        Future.sequence(in)
    }
}