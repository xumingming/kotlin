/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.utils

interface Edges<T> {
    fun getNeighbors(node: T): List<T>
}

interface Graph<T> : Edges<T> {
    val nodes: Set<T>
}

fun <T> Edges<T>.isInCycle(from: T): Boolean {
    var result = false

    val visited = object : DFS.VisitedWithSet<T>() {
        override fun checkAndMarkVisited(current: T): Boolean {
            val added = super.checkAndMarkVisited(current)
            if (!added && current == from) {
                result = true
            }
            return added
        }

    }

    val handler = object : DFS.AbstractNodeHandler<T, Unit>() {
        override fun result() {}
    }

    val neighbors = object : DFS.Neighbors<T> {
        override fun getNeighbors(current: T) = this@isInCycle.getNeighbors(current)
    }

    DFS.dfs(listOf(from), neighbors, visited, handler)

    return result
}
