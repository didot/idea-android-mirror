/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.profilers.cpu.capturedetails

import com.android.tools.adtui.model.Range
import com.android.tools.perflib.vmtrace.ClockType
import com.android.tools.profilers.cpu.CaptureNode
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel
import com.android.tools.profilers.cpu.nodemodel.SingleNameModel
import java.util.IdentityHashMap
import java.util.Stack

/**
 * The full aggregation (e.g. top-down/bottom-up) not restricted to any range,
 * that expands lazily
 */
abstract class Aggregate<T: Aggregate<T>> {
  abstract val id: String
  abstract val nodes: List<CaptureNode>
  abstract val children: List<T>
  abstract val methodModel: CaptureNodeModel
  abstract val filterType: CaptureNode.FilterType
  val isUnmatched get() = filterType == CaptureNode.FilterType.UNMATCH
  abstract fun totalOver(clockType: ClockType, range: Range): Summary
  fun overlapsWith(range: Range) = nodes.any { it.start < range.max && range.min < it.end }
  data class Summary(val total: Double, val childrenTotal: Double)

  class TopDown private constructor(override val id: String, override val nodes: List<CaptureNode>): Aggregate<TopDown>() {
    override val methodModel: CaptureNodeModel get() = nodes[0].data
    override val filterType: CaptureNode.FilterType get() = nodes[0].filterType
    override val children: List<TopDown> = lazyList(
      { nodes.asSequence()
        .flatMap(CaptureNode::children)
        .groupBy { it.isUnmatched to it.data.id }
        .map { (key, nodes) -> TopDown(key.second, nodes) }
      },
      { nodes.all { it.childCount == 0 } }
    )

    override fun totalOver(clockType: ClockType, range: Range): Summary {
      var total = 0.0
      var childrenTotal = 0.0
      for (node in nodes) {
        total += getIntersection(range, node, clockType)
        for (child in node.children) {
          childrenTotal += getIntersection(range, child, clockType)
        }
      }
      return Summary(total, childrenTotal)
    }

    companion object {
      @JvmStatic fun rootAt(node: CaptureNode) = TopDown(node.data.id, listOf(node))
    }
  }

  /**
   * Represents a bottom-up node in the bottom-view. To create a new bottom-up graph
   * at a {@link CaptureNode}, see {@link BottomUpNode.rootAt(CaptureNode)}
   */
  sealed class BottomUp private constructor(override val id: String): Aggregate<BottomUp>() {
    class Root(node: CaptureNode): BottomUp("Root") {
      override val nodes = listOf(node)
      override val children = buildChildren(node.preOrderTraversal().map { it to it })
      override val methodModel = SingleNameModel("") // sample entry for the root
      override val filterType get() = CaptureNode.FilterType.MATCH
    }
    class Child(id: String, private val pathNodes: List<CaptureNode>, override val nodes: List<CaptureNode>): BottomUp(id) {
      override val methodModel get() = pathNodes[0].data
      override val filterType get() = pathNodes[0].filterType
      override val children: List<Child> = lazyList(
        { buildChildren((pathNodes.asSequence() zip nodes.asSequence())
                          .mapNotNull { (pathNode, node) -> pathNode.parent?.let { it to node } })
        },
        { pathNodes.all { it.parent == null } }
      )
    }

    override fun totalOver(clockType: ClockType, range: Range): Summary {
      // how much time was spent in this call stack path, and in the functions it called
      var total = 0.0

      // The node that is at the top of the call stack, e.g if the call stack looks like B [0..30] -> B [1..20],
      // then the second method can't be outerSoFarByParent.
      // It's used to exclude nodes which aren't at the top of the
      // call stack from the total time calculation.
      // When multiple threads with the same ID are selected, the nodes are merged. When this happens nodes may be interlaced between
      // each of the threads. As such we keep a mapping of outer so far by parents to keep the book keeping done properly.
      val outerSoFarByParent = IdentityHashMap<CaptureNode, CaptureNode>()

      // how much time was spent doing work directly in this call stack path
      var self = 0.0
      // myNodes is sorted by CaptureNode#getStart() in increasing order,
      // if they are equal then ancestor comes first
      for (node in nodes) {
        // We use the root node to distinguish if two nodes share the same tree. In the event of multi-select we want to compute the bottom
        // up calculation independently for each tree then sum them after the fact.
        // TODO(153306735): Cache the root calculation, otherwise our update algorithm is going to be O(n*depth) instead of O(n)
        val root = node.findRootNode()
        val outerSoFar = outerSoFarByParent[root]
        if (outerSoFar == null || node.end > outerSoFar.end) {
          if (outerSoFar != null) {
            // |outerSoFarByParent| is at the top of the call stack
            total += getIntersection(range, outerSoFar, clockType)
          }
          outerSoFarByParent[root] = node
        }
        self += getIntersection(range, node, clockType) -
                node.children.sumOf { getIntersection(range, it, clockType) }
      }
      for (outerSoFar in outerSoFarByParent.values) {
        // |outerSoFarByParent| is at the top of the call stack
        total += getIntersection(range, outerSoFar, clockType)
      }
      val childrenTotal = total - self
      return Summary(total, childrenTotal)
    }

    companion object {
      @JvmStatic fun rootAt(node: CaptureNode): BottomUp = Root(node)

      protected fun buildChildren(pathNodesAndNodes: Sequence<Pair<CaptureNode, CaptureNode>>): List<Child> =
        pathNodesAndNodes
          // We use a separate map for unmatched children, because we can not merge unmatched with matched,
          // i.e all merged children should have the same {@link CaptureNode.FilterType};
          .groupBy { (pathNode, _) -> pathNode.isUnmatched to pathNode.data.id }
          .map { (key, nodePairs) ->
            val (pathNodes, nodes) = nodePairs.unzip()
            Child(key.second, pathNodes, nodes)
          }

      private fun CaptureNode.preOrderTraversal() = sequence<CaptureNode> {
        val stack = Stack<CaptureNode>().also { it.add(this@preOrderTraversal) }
        while (stack.isNotEmpty()) {
          val next = stack.pop()
          stack.addAll(next.children.asReversed()) // reversed order so that first child is processed first
          // If we don't have an Id then we exclude this node from being added as a child to the parent.
          // The only known occurrence of this is the empty root node used to aggregate multiple selected objects.
          if (next.data.id.isNotEmpty()) yield(next)
        }
      }
    }
  }

  companion object {
    internal fun getIntersection(range: Range, node: CaptureNode, type: ClockType): Double = when (type) {
      ClockType.GLOBAL -> range.getIntersectionLength(node.startGlobal.toDouble(), node.endGlobal.toDouble())
      ClockType.THREAD -> range.getIntersectionLength(node.startThread.toDouble(), node.endThread.toDouble())
    }
  }
}

/**
 * List whose content is generated lazily, and whose emptiness can be checked by
 * (trusted) external knowledge without computing the content
 */
private fun<T> lazyList(getContent: () -> List<T>, checkEmpty: () -> Boolean) = object: List<T> {
  private val content by lazy(getContent)
  private val _empty by lazy(checkEmpty)
  override val size get() = content.size
  override fun get(index: Int) = content[index]
  override fun isEmpty() = _empty
  override fun iterator() = content.iterator()
  override fun listIterator() = content.listIterator()
  override fun listIterator(index: Int) = content.listIterator(index)
  override fun subList(fromIndex: Int, toIndex: Int) = content.subList(fromIndex, toIndex)
  override fun lastIndexOf(element: T) = content.lastIndexOf(element)
  override fun indexOf(element: T) = content.indexOf(element)
  override fun containsAll(elements: Collection<T>) = content.containsAll(elements)
  override fun contains(element: T) = content.contains(element)
}