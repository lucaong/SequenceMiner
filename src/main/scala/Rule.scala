package com.lucaongaro.sequenceminer

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
