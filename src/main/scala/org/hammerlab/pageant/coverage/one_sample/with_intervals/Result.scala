package org.hammerlab.pageant.coverage.one_sample.with_intervals

import java.io.PrintWriter

import org.apache.hadoop.fs.Path
import org.apache.spark.rdd.RDD
import org.hammerlab.csv.ProductsToCSV._
import org.hammerlab.genomics.reference.NumLoci
import org.hammerlab.pageant.coverage.CoverageDepth.getJointHistogramPath
import org.hammerlab.pageant.coverage.one_sample
import org.hammerlab.pageant.coverage.one_sample.Count
import org.hammerlab.pageant.coverage.one_sample.with_intervals.ResultBuilder.DC
import org.hammerlab.pageant.histogram.JointHistogram
import org.hammerlab.pageant.histogram.JointHistogram._
import org.hammerlab.pageant.utils.{ WriteLines, WriteRDD }

/**
 * Statistics about one set of reads' coverage of a set of intervals.
 *
 * @param jh joint-histogram of read coverage vs. interval coverage (the latter being 1 or 0 everywhere).
 * @param pdf ([[Depth]], [[Count]]) tuples indicating on-target and off-target coverage at all observed depths.
 * @param cdf CDF of the PDF above; tuples represent numbers of on- and off-target loci with depth *at least* a given
 *            number.
 * @param readsStats summary depth/coverage stats about the reads-set.
 * @param filteredCDF summary CDF, filtered to a few logarithmically-spaced round-numbers.
 * @param totalOnLoci total number of on-target loci.
 * @param totalOffLoci total number of off-target loci observed to have non-zero coverage.
 */
case class Result(jh: JointHistogram,
                  pdf: RDD[DC],
                  cdf: RDD[DC],
                  readsStats: ReadSetStats,
                  filteredCDF: Array[DC],
                  totalOnLoci: NumLoci,
                  totalOffLoci: NumLoci)
  extends one_sample.Result {

  @transient lazy val ReadSetStats(maxDepth, totalBases, onBases) = readsStats

  def toCSVRow(depthCounts: DC): CSVRow =
    CSVRow(depthCounts, totalBases, totalOnLoci, totalOffLoci)

  override def save(dir: String,
           force: Boolean = false,
           writeFullDistributions: Boolean = false,
           writeJointHistogram: Boolean = false): Unit = {

    val fs = new Path(dir).getFileSystem(jh)

    if (writeFullDistributions) {
      WriteRDD(dir, s"pdf", pdf.map(toCSVRow), force, jh)
      WriteRDD(dir, s"cdf", cdf.map(toCSVRow), force, jh)
    }

    if (writeJointHistogram) {
      val jhPath = getJointHistogramPath(dir)

      if (fs.exists(jhPath)) {
        fs.delete(jhPath, true)
      }

      jh.write(jhPath)
    }

    WriteLines(dir, s"cdf.csv", filteredCDF.map(CSVRow(_, totalBases, totalOnLoci, totalOffLoci)).toCSV(), force, jh)

    val miscPath = new Path(dir, "misc")
    if (force || !fs.exists(miscPath)) {
      val pw = new PrintWriter(fs.create(miscPath))
      pw.println(s"Max depth: $maxDepth")
      pw.println(s"Total mapped bases: $totalBases")
      pw.println(s"Total on-target bases: $onBases")
      pw.println(s"Total on-target loci: $totalOnLoci")
      pw.close()
    }
  }
}


