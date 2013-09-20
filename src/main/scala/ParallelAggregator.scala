package com.lucaongaro.sequenceminer

import akka.actor.{ Actor, ActorSystem, ActorRef, Props }

class ParallelAggregator[A](
  iter: Iterator[A]
)( implicit system: ActorSystem ) {

  case class  Item[B]( z: B, item: Option[A] )
  case class  Extraction[B]( partialResult: B )
  case object Start
  case class  Done[B]( result: B )

  private class Worker[B](
    extract: ( B, A ) => B
  ) extends Actor {
    def receive = {
      case Item(z, None) => 
        sender ! Done(z)
      case Item(z, Some(item)) =>
        sender ! Extraction( extract( z.asInstanceOf[B], item.asInstanceOf[A] ) )
    }
  }

  private class Manager[B](
    z:       B,
    extract: ( B, A ) => B,
    combine: ( B, B ) => B
  ) extends Actor {

    val workers = Vector.tabulate( 100 ) {
      ( i ) => context.actorOf( Props( new Worker[B]( extract ) ),
        "parallel-aggregator-worker-" + i )
    }

    private var combination   = z
    private var activeWorkers = workers.size
    private var caller: ActorRef = _

    def receive = {
      case Start => {
        caller = sender
        workers.foreach { _ ! Item(z, optionalNext()) }
        context.become( receiveExtractions )
      }
    }

    def receiveExtractions: Receive = {
      case Extraction(partialResult) =>
        sender ! Item(partialResult, optionalNext())
      case Done(result) => {
        context.stop( sender )
        activeWorkers -= 1
        combination = combine( combination, result.asInstanceOf[B] )
        if ( activeWorkers == 0 ) {
          this.caller ! combination
        }
      }
    }
  }

  private def optionalNext(): Option[A] = {
    if ( iter.hasNext ) Some(iter.next) else None
  }

  def parallelAggregate[B](z: B)(
    extract: (B, A) => B,
    combine: (B, B) => B
  ): B = {
    import scala.concurrent.duration._
    import akka.util.Timeout
    import akka.pattern.ask
    import scala.concurrent.Await

    implicit val timeout = Timeout( 10 minutes )

    val manager = system.actorOf(
      Props( new Manager( z, extract, combine ) )
    )

    val future = manager ? Start
    val result = Await.result( future, Duration.Inf )
    system.stop( manager )
    result.asInstanceOf[B]
  }
}
