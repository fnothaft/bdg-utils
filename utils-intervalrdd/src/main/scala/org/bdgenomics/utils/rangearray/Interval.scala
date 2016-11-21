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

package org.bdgenomics.utils.rangearray

/**
 * An interval is a 2-dimensional 0-based coordinate consisting of a closed start value and open end value.
 * Each 2-dimensional coordinate has a defined width.
 * This can be used to express a region of a genome, a transcript, a gene, etc.
 *
 * @tparam T Recursive type extending Interval. Used for return type of
 * functions overlap() and compareTo()
 */
trait Interval[T <: Interval[T]] extends Comparable[T] {

  /**
   * @return The start of this interval.
   */
  def start: Long

  /**
   * @return The end of this interval.
   */
  def end: Long

  /**
   * A width is the key property of an interval, which can represent a genomic
   * region, a transcript, a gene, etc.
   *
   * @return The width of this interval.
   */
  def width: Long = end - start

  /**
   * Determines whether Interval intersects with another Interval T.
   *
   * @param interval Another interval to compare against.
   * @return Boolean whether or not Intervals overlap
   *
   * @see covers
   */
  def overlaps(interval: T): Boolean

  /**
   * Determines whether the range of positions this interval intersects the
   * range of positions another interval covers.
   *
   * In many cases, this function is identical to the overlaps function.
   * However, some coordinate spaces may allow two intervals to cover the same
   * start/end interval, while not truly overlapping. E.g., in a genomic
   * coordinate space, two objects may cover the same range on a chromosome, but
   * may have opposite strandedness.
   *
   * Essentially, this is relaxed variant of overlaps where we have projected
   * a complex coordinate space down. If two intervals overlap, they must cover
   * each other. However, if two interlaps cover each other, they may not
   * overlap.
   *
   * @param interval Another interval to compare against.
   * @return True if two intervals cover intersecting indices in a coordinate
   *   space.
   *
   * @see overlaps
   */
  def covers(interval: T): Boolean

  /**
   * Compares the distance between this Interval and other Interval T.
   *
   * @param interval Another interval to compare against.
   * @return Greater than/equal to/less than comparison.
   */
  def compareTo(interval: T): Int

  /**
   * Provides a distance, if defined, between two Intervals.
   *
   * @param interval Another interval to compare against.
   * @return Absolute value istance between two Intervals, if defined.
   */
  def distance(interval: T): Option[Long]
}
