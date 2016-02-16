package org.hammerlab.pageant.suffixes

import org.apache.spark.serializer.DirectFileRDDSerializer._
import org.hammerlab.pageant.utils.{KryoNoReferenceTracking, PageantSuite}
import org.hammerlab.pageant.utils.Utils.loadBam
import org.scalatest.{FunSuite, Matchers}

import scala.collection.mutable.ArrayBuffer


class PDC3Test extends SuffixArrayTestBase with PageantSuite with KryoNoReferenceTracking {

  override def testFn(name: String)(testFun: => Unit): Unit = test(name)(testFun)

  import PDC3.{ItoT, TtoI}

  override def arr(a: Array[Int], n: Int): Array[Int] = {
    PDC3.apply(sc.parallelize(a.map(ItoT))).map(TtoI).collect
  }

  test("bam") {
    val ots = loadBam(sc, "normal.bam")
    val ts = ots.zipWithIndex().map(_.swap).sortByKey(numPartitions = 4).map(_._2)

    ots.getNumPartitions should be(1)
    ts.getNumPartitions should be(4)

    ots.take(10) should be(Array(1, 4, 4, 4, 4, 4, 1, 1, 3, 1))
    ts.take(10) should be(Array(1, 4, 4, 4, 4, 4, 1, 1, 3, 1))

    ts.count should be(102000)

    val sa = PDC3.apply(ts.map(_.toLong))
    sa.count should be(102000)

    sa.take(1000) should be(1 to 1000 map(_ * 102 - 1) toArray)

    sa.getNumPartitions should be(12)

    ts.saveAsDirectFile("src/test/resources/normal.bam.ts", gzip = true)
//    val ts2 = sc.directFile[Byte]("src/test/resources/normal.bam.ts", gzip = true)
//    ts2.getNumPartitions should be(4)
//    ts2.count should be(102000)
//
//    ts.saveAsDirectFile("src/test/resources/normal.bam.ts", gzip = false)
//    val ts3 = sc.directFile[Byte]("src/test/resources/normal.bam.ts", gzip = false)
//    ts3.getNumPartitions should be(4)
//    ts3.count should be(102000)

    sa.saveAsDirectFile("src/test/resources/normal.bam.sa", gzip = false)
//    val sa2 = sc.directFile[Long]("src/test/resources/normal.bam.sa", gzip = false)
//    sa2.count should be(102000)
//
//    sa.saveAsDirectFile("src/test/resources/normal.bam.sa", gzip = true)
//    val sa3 = sc.directFile[Long]("src/test/resources/normal.bam.sa", gzip = true)
//    sa3.count should be(102000)
  }
}

class CmpFnTest extends FunSuite with Matchers {
  import PDC3.{Joined, cmpFn, zero}

  test("basic 1-1 cmp") {
    cmpFn(
      (1, Joined(t0O = Some(2), n0O = Some(3), n1O = Some(4))),
      (4, Joined(t0O = Some(2), n0O = Some(2), n1O = Some(1)))
    ) should be > 0
  }

  test("basic 2-0 cmp") {
    val t1 = (zero + 5, Joined(t0O = Some(2), n0O = Some(1)))
    val t2 = (zero + 3, Joined(t0O = Some(1), t1O = Some(2), n0O = Some(2), n1O = Some(1)))

    cmpFn(t1, t2) should be > 0
    cmpFn(t2, t1) should be < 0
  }
}
