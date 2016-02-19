package org.hammerlab.pageant.serialization

import java.nio.ByteBuffer

import org.apache.hadoop.io.{BytesWritable, NullWritable}
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkContext, SparkEnv}

import scala.reflect.ClassTag

class SequenceFileSerializableRDD[T: ClassTag](@transient val rdd: RDD[T]) extends Serializable {
  def serializeToSequenceFile(path: String): RDD[T] = {
    rdd.mapPartitions(iter => {
      val serializer = SparkEnv.get.serializer.newInstance()
      iter.map(x =>
        (
          NullWritable.get(),
          new BytesWritable(serializer.serialize(x).array())
        )
      )
    }).saveAsSequenceFile(path)

    rdd
  }
}

object SequenceFileSerializableRDD {
  implicit def toSerializableRDD[T: ClassTag](rdd: RDD[T]): SequenceFileSerializableRDD[T] = new SequenceFileSerializableRDD(rdd)
  implicit def toSerdeSparkContext(sc: SparkContext): SequenceFileSparkContext = new SequenceFileSparkContext(sc)
}

class SequenceFileSparkContext(val sc: SparkContext) {
  def fromSequenceFile[T](path: String)(implicit ct: ClassTag[T]): RDD[T] = {
    sc.sequenceFile(path, classOf[NullWritable], classOf[BytesWritable], 2)
    .mapPartitions[T](iter => {
      val serializer = SparkEnv.get.serializer.newInstance()
      iter.map(x => {
        serializer.deserialize(ByteBuffer.wrap(x._2.getBytes))
      })
    })
  }
}

