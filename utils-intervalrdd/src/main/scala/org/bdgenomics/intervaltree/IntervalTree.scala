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

package org.bdgenomics.intervaltree

import scala.reflect.ClassTag
import scala.collection.mutable.ListBuffer

/*
 * Interval tree that supports two dimensional range searches over keyed nodes
 * in tree
 */
class IntervalTree[K <: Interval, T: ClassTag] extends Serializable {
  var root: Node[K, T] = null
  var leftDepth: Long = 0
  var rightDepth: Long = 0
  val threshold = 15
  var nodeCount: Long = 0

  /**
   * Creates a cloned snapshot of the current interval tree
   * @return new interval tree
   */
  def snapshot(): IntervalTree[K, T] = {
    val newTree: IntervalTree[K, T] = new IntervalTree[K, T]()
    val nodes: List[Node[K, T]] = inOrder()
    newTree.insertRecursive(nodes)
    newTree
  }

  /**
   * Gets all elements in tree
   * @return Elements in tree keyed by node's interval
   */
  def get(): List[(K, T)] = {
    inOrder().flatMap(r => r.get.toList)
  }

  /**
   * @return total number of nodes in tree
   */
  def countNodes(): Long = {
    nodeCount
  }

  /**
   * @return total number of data elements in tree
   */
  def size(): Long = {
    count
  }

  /**
   * @param nodes Nodes to construct interval tree from
   */
  def this(nodes: List[Node[K, T]]) = {
    this
    this.insertRecursive(nodes)
  }

  /**
   *
   * @param nT Interval tree to merge into this tree
   * @return new tree with nodes from nT merged into current tree
   *
   */
  def merge(nT: IntervalTree[K, T]): IntervalTree[K, T] = {
    val newNodes: List[Node[K, T]] = nT.inOrder()
    val newTree = this.snapshot()
    newTree.insertRecursive(newNodes)
    newTree
  }

  /**
   * Prints all nodes in tree in ascending order of key start value
   */
  def printNodes(): Unit = {
    println("Printing all nodes in interval tree")
    val nodes: List[Node[K, T]] = inOrder().
      sortWith(_.getInterval.start < _.getInterval.start)
    nodes.foreach(r => {
      println(r.getInterval)
      r.data.foreach(e => println(e))
    })
  }

  /**
   * Inserts single value into tree
   * @param k key of element
   * @param v value of element
   */
  def insert(k: K, v: T): Unit = {
    insert(k, Iterator(v))
  }

  /**
   *
   * @param k key to insert values
   * @param vs values to insert associated with key r
   */
  def insert(k: K, vs: Iterator[T]): Unit = {
    insertRegion(k, vs)
    if (Math.abs(leftDepth - rightDepth) > threshold) {
      rebalance()
    }
  }

  /*
  * Finds an existing node (keyed by Interval) to insert the data into,
  * or creates a new node to insert it into the tree
  * @param interval interval to insert or to create new node from
  * @param vs: values associated with interval to insert into tree
  */
  private def insertRegion(interval: K, vs: Iterator[T]) = {
    if (root == null) {
      nodeCount += 1
      root = new Node[K, T](interval)
      root.multiput(vs)
    }
    var curr: Node[K, T] = root
    var parent: Node[K, T] = null
    var search: Boolean = true
    var leftSide: Boolean = false
    var rightSide: Boolean = false
    var tempLeftDepth: Long = 0
    var tempRightDepth: Long = 0

    while (search) {
      curr.subtreeMax = Math.max(curr.subtreeMax, interval.end)
      parent = curr
      if (curr.greaterThan(interval)) { //left traversal
        if (!leftSide && !rightSide) {
          leftSide = true
        }
        tempLeftDepth += 1
        curr = curr.leftChild
        if (curr == null) {
          curr = new Node(interval)
          curr.multiput(vs)
          parent.leftChild = curr
          nodeCount += 1
          search = false
        }
      } else if (curr.lessThan(interval)) { //right traversal
        if (!leftSide && !rightSide) {
          rightSide = true
        }
        tempRightDepth += 1
        curr = curr.rightChild
        if (curr == null) {
          curr = new Node(interval)
          curr.multiput(vs)
          parent.rightChild = curr
          nodeCount += 1
          search = false
        }
      } else { // insert new id, given id is not in tree
        curr.multiput(vs)
        search = false
      }
    }
    // done searching, set our max depths
    if (tempLeftDepth > leftDepth) {
      leftDepth = tempLeftDepth
    } else if (tempRightDepth > rightDepth) {
      rightDepth = tempRightDepth
    }
  }

  /**
   * Searches for all nodes that overlap key
   * @param k key to search over tree
   */
  def search(k: K): Iterator[(K, T)] = {
    search(k, root)
  }

  /**
   * maps all values in tree with a specified predicate
   * @param f predicate function to map values
   * @tparam T2 new mapped type
   * @return new interval tree of mapped values
   */
  def mapValues[T2: ClassTag](f: T => T2): IntervalTree[K, T2] = {
    val mappedList: List[Node[K, T2]] =
      inOrder.map(elem => {
        Node(elem.getInterval, elem.data.map(f))
      })
    new IntervalTree[K, T2](mappedList)

  }

  /**
   * Constructs a new tree by applying a predicate over the existing tree
   *
   * @param pred predicate function to filter vales
   * @return new interval tree of filtered values
   */
  def filterTree(pred: T => Boolean): IntervalTree[K, T] = {
    val orig: List[Node[K, T]] = inOrder()
    val filteredNodes =
      inOrder.map(elem => {
        Node(elem.getInterval, elem.data.filter(pred))
      })
    new IntervalTree[K, T](filteredNodes)
  }

  /**
   * searches tree at a subtree for a specified key
   * @param r: key to search over
   * @param n node which begins the subtree to search over
   * @return Iterator of values matching the key searched for
   */
  private def search(r: K, n: Node[K, T]): Iterator[(K, T)] = {
    val results = new ListBuffer[(K, T)]()
    if (n != null) {
      if (n.overlaps(r)) {
        results ++= n.get
      }
      if (n.subtreeMax < r.start) {
        return results.distinct.toIterator
      }
      if (n.leftChild != null) {
        results ++= search(r, n.leftChild)
      }
      if (n.rightChild != null) {
        results ++= search(r, n.rightChild)
      }
    }
    return results.distinct.toIterator
  }

  /*
  * This method is used for bulk insertions of Nodes into a tree,
  * specifically with regards to rebalancing
  * Note: this method only appends data to existing nodes if a node with the
  *   same exact Interval exists. In insertRegion, it will insert the data
  *   if the Interval is a subregion of a particular Node.
  *   @param n Node to insert into tree
  */
  def insertNode(n: Node[K, T]): Unit = {
    if (root == null) {
      root = n
      nodeCount += 1
      return
    }
    var curr: Node[K, T] = root
    var parent: Node[K, T] = null
    var search: Boolean = true
    var leftSide: Boolean = false
    var rightSide: Boolean = false
    var tempLeftDepth: Long = 0
    var tempRightDepth: Long = 0
    while (search) {
      curr.subtreeMax = Math.max(curr.subtreeMax, n.getInterval.end)
      parent = curr
      if (curr.greaterThan(n.getInterval)) { //left traversal
        if (!leftSide && !rightSide) {
          leftSide = true
        }
        tempLeftDepth += 1
        curr = curr.leftChild
        if (curr == null) {
          parent.leftChild = n
          nodeCount += 1
          search = false
        }
      } else if (curr.lessThan(n.getInterval)) { //right traversal
        if (!leftSide && !rightSide) {
          rightSide = true
        }
        tempRightDepth += 1
        curr = curr.rightChild
        if (curr == null) {
          parent.rightChild = n
          nodeCount += 1
          search = false
        }
      } else { // attempting to replace a node already in tree. Merge
        curr.multiput(n.get().map(_._2))
        search = false
      }
    }
    // done searching, now let's set our max depths
    if (tempLeftDepth > leftDepth) {
      leftDepth = tempLeftDepth
    } else if (tempRightDepth > rightDepth) {
      rightDepth = tempRightDepth
    }
  }

  /**
   * inserts all nodes recursively into tree. If nodes are sorted, this
   * will produce a balanced tree
   * @param nodes nodes to insert into tree
   */
  private def insertRecursive(nodes: List[Node[K, T]]): Unit = {
    if (nodes == null) {
      return
    }
    if (!nodes.isEmpty) {
      val count = nodes.length
      val middle = count / 2
      val node = nodes(middle)

      insertNode(node)
      insertRecursive(nodes.take(middle))
      insertRecursive(nodes.drop(middle + 1))
    }
  }

  /**
   * Gets an inorder list of nodes in tree and rebalances tree
   * @see insertRecursive
   */
  private def rebalance() = {
    val nodes: List[Node[K, T]] = inOrder()
    root = null
    nodeCount = 0
    val orderedList = nodes.sortWith(_.getInterval.start < _.getInterval.start)
    orderedList.foreach(n => n.clearChildren())
    insertRecursive(orderedList)
  }

  /**
   * generates in order list of nodes
   * @return sorted list of nodes in tree
   */
  private def inOrder(): List[Node[K, T]] = {
    return inOrder(root).toList
  }

  /**
   * counts the number of elements from all nodes past the root
   * @return number of elements
   */
  private def count(): Long = {
    count(root)
  }

  /**
   * counts the number of elements from all nodes past a specified node
   * @param n node to count from
   * @return count of elements in subtree starting at node n
   */
  private def count(n: Node[K, T]): Long = {
    var total: Long = 0
    if (n == null) {
      return total
    }
    total += n.getSize
    total += count(n.leftChild)
    total += count(n.rightChild)
    total
  }

  /**
   * generates an in order list of nodes starting at a specified node
   * @param n node to begin in order traversal from
   * @return list of in order nodes in subtree starting at node n
   */
  private def inOrder(n: Node[K, T]): List[Node[K, T]] = {
    if (n == null) {
      return List.empty[Node[K, T]]
    }

    val seen = new ListBuffer[Node[K, T]]()
    seen += n.clone

    seen ++= inOrder(n.leftChild)
    seen ++= inOrder(n.rightChild)
    seen.toList
  }
}
