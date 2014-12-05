/**
 * Licensed to Big Data Genomics (BDG) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The BDG licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.spark.rdd

import org.apache.hadoop.conf.Configuration
import org.apache.spark.SparkContext._
import org.bdgenomics.utils.instrumentation._
import scala.reflect.ClassTag

/**
 * Contains methods for instrumenting Spark RDD operations. To instrument a particular operation, simply
 * import the implicits from [[org.bdgenomics.adam.rdd.ADAMContext]] and replace calls to RDD operations with
 * the versions prefixed by `instrumented`. For example, replace calls to `map` with `instrumentedMap`.
 *
 * This class does not try to provide a comprehensive set of all RDD operations -- instead it is just those
 * that are currently instrumented in ADAM. However, it is generally very easy to add a new operation --
 * see the documentation in [[InstrumentedRDDFunctions]] for further details.
 */
class InstrumentedRDDFunctions[T](self: RDD[T]) extends BaseInstrumentedRDDFunctions() {

  def instrumentedGroupBy[K](f: T => K)(implicit kt: ClassTag[K]): RDD[(K, Iterable[T])] = {
    recordOperation {
      val recorder = metricsRecorder()
      self.groupBy((t: T) => { recordFunction(f(t), recorder, FunctionTimers.GroupByFunction) })
    }
  }

  def instrumentedMap[U: ClassTag](f: T => U): RDD[U] = {
    recordOperation {
      val recorder = metricsRecorder()
      self.map((t: T) => { recordFunction(f(t), recorder, FunctionTimers.MapFunction) })
    }
  }

  def instrumentedKeyBy[K](f: T => K): RDD[(K, T)] = {
    recordOperation {
      val recorder = metricsRecorder()
      self.keyBy((t: T) => { recordFunction(f(t), recorder, FunctionTimers.KeyByFunction) })
    }
  }

  def instrumentedFlatMap[U: ClassTag](f: T => TraversableOnce[U]): RDD[U] = {
    recordOperation {
      val recorder = metricsRecorder()
      self.flatMap((t: T) => { recordFunction(f(t), recorder, FunctionTimers.FlatMapFunction) })
    }
  }

  def instrumentedFilter(f: T => Boolean): RDD[T] = {
    recordOperation {
      val recorder = metricsRecorder()
      self.filter((t: T) => { recordFunction(f(t), recorder, FunctionTimers.FilterFunction) })
    }
  }

  def instrumentedAggregate[U: ClassTag](zeroValue: U)(seqOp: (U, T) => U, combOp: (U, U) => U): U = {
    recordOperation {
      val recorder = metricsRecorder()
      self.aggregate(zeroValue)(
        (u: U, t: T) => { recordFunction(seqOp(u, t), recorder, FunctionTimers.AggregateSeqFunction) },
        (u: U, u2: U) => { recordFunction(combOp(u, u2), recorder, FunctionTimers.AggregateCombFunction) })
    }
  }

  def instrumentedMapPartitions[U: ClassTag](f: Iterator[T] => Iterator[U], preservesPartitioning: Boolean = false): RDD[U] = {
    recordOperation {
      val recorder = metricsRecorder()
      self.mapPartitions((t: Iterator[T]) => { recordFunction(f(t), recorder, FunctionTimers.MapPartitionsFunction) })
    }
  }

  def instrumentedFold(zeroValue: T)(op: (T, T) => T): T = {
    recordOperation {
      val recorder = metricsRecorder()
      self.fold(zeroValue)((t: T, t2: T) => { recordFunction(op(t, t2), recorder, FunctionTimers.FoldFunction) })
    }
  }

  def instrumentedFirst(): T = {
    recordOperation {
      self.first()
    }
  }

  def instrumentedRepartition(numPartitions: Int)(implicit ord: Ordering[T] = null): RDD[T] = {
    recordOperation {
      self.repartition(numPartitions)
    }
  }

  def instrumentedCoalesce(numPartitions: Int, shuffle: Boolean = false)(implicit ord: Ordering[T] = null): RDD[T] = {
    recordOperation {
      self.coalesce(numPartitions, shuffle)
    }
  }

}

class InstrumentedPairRDDFunctions[K, V](self: RDD[(K, V)])(implicit kt: ClassTag[K], vt: ClassTag[V], ord: Ordering[K] = null)
    extends BaseInstrumentedRDDFunctions() {
  implicit val sc = self.sparkContext
  def instrumentedSaveAsNewAPIHadoopFile(path: String, keyClass: Class[_], valueClass: Class[_],
                                         outputFormatClass: Class[_ <: InstrumentedOutputFormat[_, _]], conf: Configuration = self.context.hadoopConfiguration) {
    recordOperation {
      instrumentSaveAsNewAPIHadoopFile(self, path, keyClass, valueClass, outputFormatClass, conf)
    }
  }
}

class InstrumentedOrderedRDDFunctions[K: Ordering: ClassTag, V: ClassTag](self: RDD[(K, V)])
    extends BaseInstrumentedRDDFunctions() {
  implicit val sc = self.sparkContext
  def instrumentedSortByKey(ascending: Boolean = true, numPartitions: Int = self.partitions.size): RDD[(K, V)] = {
    recordOperation {
      self.sortByKey(ascending, numPartitions)
    }
  }
}

object FunctionTimers extends Metrics {
  val GroupByFunction = timer("groupBy function")
  val MapFunction = timer("map function")
  val KeyByFunction = timer("keyBy function")
  val FlatMapFunction = timer("flatMap function")
  val FilterFunction = timer("filter function")
  val AggregateSeqFunction = timer("aggregate seq. function")
  val AggregateCombFunction = timer("aggregate comb. function")
  val MapPartitionsFunction = timer("mapPartitions function")
  val FoldFunction = timer("fold function")
}
