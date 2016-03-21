package org.hammerlab.pageant.gunzp

import java.io.{FileNotFoundException, IOException}
import java.util.zip.GZIPInputStream

import org.apache.commons.compress.archivers.{ArchiveException, ArchiveStreamFactory}
import org.apache.commons.io.IOUtils
import org.apache.hadoop.fs.Path
import org.apache.spark.{SparkConf, SparkContext}

object Untar {

  def main(args: Array[String]): Unit = {

    val inputPath = args(1)
    val outputPath =
      if (args.length > 2)
        args(2)
      else if (inputPath.endsWith(".tar.gz"))
        inputPath.dropRight(7)
      else if (inputPath.endsWith(".tar"))
        inputPath.dropRight(4)
      else
        throw new Exception(s"Suspicious extension: $inputPath")

    val conf = new SparkConf()
    conf.setAppName(s"Gunzp:$outputPath")
    val sc = new SparkContext(conf)

    unTar(sc, inputPath, outputPath, inputPath.endsWith(".gz"))
  }

  /**
    * Adapted from http://stackoverflow.com/a/7556307/544236.
    *
    * Untar an input file into an output file.
    *
    * The output file is created in the output folder, having the same name
    * as the input file, minus the '.tar' extension.
    *
    * @param inputFile     the input .tar file
    * @param outputFile     the output directory file.
    * @throws IOException
    * @throws FileNotFoundException
    * @throws ArchiveException
    */
  def unTar(sc: SparkContext, inputFile: String, outputFile: String, gzip: Boolean) {

    val inputPath = new Path(inputFile)
    val outputDir = new Path(outputFile)
    println(s"Untaring $inputFile to dir $outputDir")

    val fs = inputPath.getFileSystem(sc.hadoopConfiguration)
    val rawIs = fs.open(inputPath)
    val is =
      if (gzip)
        new GZIPInputStream(rawIs)
      else
        rawIs
    val debInputStream = new ArchiveStreamFactory().createArchiveInputStream("tar", is)
    var looping = true
    while (looping) {
      Option(debInputStream.getNextEntry) match {
        case Some(entry) ⇒
          val outputFile = new Path(outputDir, entry.getName)
          if (entry.isDirectory) {
            println(s"Attempting to write output directory $outputFile")
            if (!fs.exists(outputFile)) {
              println(s"Attempting to create output directory $outputFile")
              if (!fs.mkdirs(outputFile)) {
                throw new IllegalStateException(s"Couldn't create directory $outputFile")
              }
            }
          } else {
            println(s"Creating output file $outputFile")
            val outputFileStream = fs.create(outputFile)
            IOUtils.copyLarge(debInputStream, outputFileStream)
            outputFileStream.close()
          }
        case _ ⇒ looping = false
      }
    }
    debInputStream.close()
  }
}
