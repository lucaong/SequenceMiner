package com.lucaongaro.sequenceminer

import scala.io.Source
import scala.language.implicitConversions
import scala.language.postfixOps
import akka.actor.ActorSystem

class SequenceMiner( filePath: String, tokenize: String => Seq[String] ) {

  def this( filePath: String, sep: String = """\s*,\s*""") {
    this( filePath, ( str ) => str.split( sep ) )
  }

  implicit val actorSystem = ActorSystem("parallel-aggregator")

  implicit def iteratorToParallelAggregator[A](
    iter: Iterator[A]
  ) = {
    new ParallelAggregator( iter )
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
