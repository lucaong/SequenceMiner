SequenceMiner
=============

A simple and experimental class to mine frequent sequences of contiguous items and generate rules based on them. It performs some operations
concurrently using Akka actors in a MapReduce fashion.

Example
-------

```scala
// Create a SequenceMiner instance using moby_dick.txt as the
// dataset and separating tokens by non-word characters (to get
// all the words)
val miner = new SequenceMiner("moby_dick.txt", """\W+""")

// Get sequences of contiguous words appearing at least 100 times
val freqSeq = miner.getFrequentSequences(100)

// Get rules from those frequent sequences with minimum
// confidence of 0.5 and minimum lift of 10
val rules = miner.getRules(freqSeq, 0.5, 10)

// Print rules:
rules.foreach( println _ )

/*
feet, in -> length (s: 10 c: 0.526 l: 1718.076)
guernsey -> man (s: 11 c: 1.000 l: 476.756)
a, sperm -> whale (s: 13 c: 0.867 l: 179.698)
number -> of (s: 15 c: 0.556 l: 20.908)
dough -> boy (s: 16 c: 0.941 l: 3638.016)
the, right -> whale (s: 25 c: 0.658 l: 136.411)
vicinity -> of (s: 11 c: 0.579 l: 21.789)
supposed -> to (s: 10 c: 0.667 l: 35.714)
owing -> to (s: 22 c: 0.917 l: 49.107)
what, do -> you (s: 10 c: 0.625 l: 167.689)
...
*/
```

Anatomy of a Rule
-----------------

A rule is composed by an antecedent and a consequent. Both of them are sequences. They are also characterized by three values: a support, a
confidence and a lift. The support is the number of occurrences of the rule; the confidence is the proportion of times that, given the
antecent, the consequent is found; the lift is how much more frequent the rule is compared to what we would expect if the antecend and the
consequent were independent events. For example, the following rule:

A, B -> C, D, E (s: 153 c: 0.784 l: 44.513)

Is read as "when A is followed by B, then also C, D and E are likely to follow". Moreover, this rule occurs 153 times. In the 78.4% of the
case in which A is followed by B, then they are also followed by C, D and E. This association is 44.513 times more frequent than what
expected if the sequences A, B and C, D, E were independent.


MIT License
===========

Copyright (c) 2013 Luca Ongaro

MIT License

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
