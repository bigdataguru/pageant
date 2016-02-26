package org.hammerlab.pageant.fm.blocks

import org.hammerlab.pageant.fm.utils.Counts
import org.hammerlab.pageant.fm.utils.Utils.toI

object Utils {
  def counts(s: String): Counts = Counts(s.trim().split("\\s+").map(_.toLong))
  def runs(str: String): Seq[BWTRun] = {
    str.split(" ").map(s ⇒ {
      val t = toI(s.last)
      val n = s.dropRight(1).toInt
      BWTRun(t, n)
    })
  }
}
