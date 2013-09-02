package eu.lucaongaro.sequenceminer

import scala.io.Source
import scala.language.implicitConversions
import scala.language.postfixOps
import akka.actor.ActorSystem

class SequenceMiner( filePath: String, tokenize: String => Seq[String] ) {

  def this( filePath: String, sep: String = """\s*,\s*""") {
    this( filePath, ( str ) => str.split( sep ) )
  }

  case class Rule(
    antecedent: Seq[String],
    consequent: Seq[String],
    support:    Int,
    confidence: Double,
    lift:       Double
  ) {
    override def toString = {
      antecedent.mkString(", ") +
      " -> " + consequent.mkString(", ") +
      " (s: " + support +
      " c: %.3f".format( confidence ) +
      " l: %.3f".format( lift ) + ")"
    }
  }

  class ParallelAggregator[A]( iter: Iterator[A], system: ActorSystem ) {
    import akka.actor.{ Actor, ActorRef, Props }

    case class  Item[B]( z: B, item: Option[A] )
    case class  Extraction[B]( partialResult: B )
    case object Combine
    case class  Done[B]( result: B )

    private class Worker[B]( extract: ( B, A ) => B ) extends Actor {
      def receive = {
        case Item(z, None) => 
          sender ! Done(z)
        case Item(z, Some(item)) =>
          sender ! Extraction( extract( z.asInstanceOf[B], item.asInstanceOf[A] ) )
      }
    }

    private class Combinator[B](
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
        case Combine => {
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

      val combinator = system.actorOf(
        Props( new Combinator( z, extract, combine ) )
      )

      val future = combinator ? Combine
      val result = Await.result( future, Duration.Inf )
      system.stop( combinator )
      result.asInstanceOf[B]
    }
  }

  val actorSystem = ActorSystem("parallel-aggregator")

  implicit def iteratorToParallelAggregator[A]( iter: Iterator[A] ) = {
    new ParallelAggregator( iter, actorSystem )
  }

  private def lineIterator = {
    Source.fromFile( filePath ).getLines
  }

  private object countToProbability {
    import scala.collection.mutable
    private val cache = mutable.Map[Int, Int]()

    def apply( count: Double, size: Int ) = {
      count / totalPossibilities( size )
    }

    private def totalPossibilities( size: Int ) = {
      if ( !cache.contains( size ) ) {
        val tot = lineIterator.parallelAggregate(0)(
          ( sum, line ) => { sum + tokenize( line ).length - size + 1 },
          ( sum1, sum2 ) => sum1 + sum2
        )
        cache( size ) = tot
      }
      cache( size )
    }
  }

  private def countSequences(
    n:        Int,
    filterFn: Seq[String] => Boolean = _ => true
  ) = {
    lineIterator.parallelAggregate( Map[Seq[String], Int]() withDefaultValue 0 )(
      ( counts, line ) => {
        tokenize( line ).sliding( n ).foldLeft( counts ) {
          ( counts, item ) => {
            val sequence = item.toList
            if ( item.length == n && filterFn( sequence ) )
              counts.updated( sequence, counts( sequence ) + 1 )
            else counts
          }
        }
      },
      ( c1, c2 ) => c1 ++ c2.map { case (k, v) => k -> ( c1( k ) + v ) }
    )
  }

  def getFrequentSequences( minSupport: Double ) = {
    val areSubsFrequent = (
      n:      Int,
      seq:    Seq[String],
      counts: Map[Seq[String], Int]
    ) => {
      n == 0 || seq.toIterator.sliding( n ).forall( counts.contains(_) )
    }

    def addLargerSequences(
      n:      Int = 1,
      counts: Map[Seq[String], Int] = Map[Seq[String], Int]()
    ): Map[Seq[String], Int] = {
      val more = countSequences( n, areSubsFrequent( n - 1, _, counts ) ).
        filter( _._2 >= minSupport )
      if ( !more.isEmpty ) {
        addLargerSequences( n + 1, counts ++ more )
      } else {
        counts
      }
    }
 
    addLargerSequences()
  }

  def getRules(
    freqSequences: Map[Seq[String], Int],
    minConfidence: Double,
    minLift:       Double = 1.0
  ) = {
    import scala.collection.mutable
    val rules = mutable.Set.empty[Rule]

    def addRulesInSequence(
      sequence: Seq[String],
      count:    Int,
      length:   Int,
      n:        Int = 1
    ) {
      val emptySet = mutable.Set.empty[Rule]
      if ( length > n ) {
        val ( prec, cons ) = sequence.splitAt( length - n )
        val confidence     = count.toDouble / freqSequences( prec )
        val lift = countToProbability( count, length ) / (
          countToProbability( freqSequences( prec ), length - n ) *
          countToProbability( freqSequences( cons ), n )
        )
        if ( confidence >= minConfidence ) {
          if ( lift >= minLift )
            rules += Rule( prec, cons, count, confidence, lift )
          addRulesInSequence( sequence, count, length, n + 1 )
        }
      }
    }

    for (
      ( sequence, count ) <- freqSequences
    ) {
      val length = sequence.length
      addRulesInSequence( sequence, count, length )
    }

    rules.toSet
  }
}
