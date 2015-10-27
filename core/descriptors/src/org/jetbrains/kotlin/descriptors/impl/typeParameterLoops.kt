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

@file:JvmName("TypeParameterLoops")
package org.jetbrains.kotlin.descriptors.impl

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.types.ErrorUtils
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.Edges
import org.jetbrains.kotlin.utils.isInCycle

fun findAndDisconnectLoopsInBounds(
        typeParameterDescriptor: TypeParameterDescriptor,
        bounds: MutableSet<KotlinType>,
        report: () -> Unit
) {
    if (bounds.isEmpty()) return
    if (bounds.size == 1 && KotlinBuiltIns.isDefaultBound(bounds.first())) return

    val result = arrayListOf<KotlinType>()

    val edges = object : Edges<TypeParameterDescriptor> {
        override fun getNeighbors(node: TypeParameterDescriptor): List<TypeParameterDescriptor> {
            val nodeBounds = if (node == typeParameterDescriptor) result else node.upperBounds

            return nodeBounds.map { it.constructor.declarationDescriptor as? TypeParameterDescriptor }.filterNotNull()
        }
    }

    var wasReported = false
    for (bound in bounds) {
        result.add(bound)
        if (edges.isInCycle(typeParameterDescriptor)) {
            result[result.lastIndex] = ErrorUtils.createErrorType("Cyclic upper bound: $bound")

            if (!wasReported) {
                report()
                wasReported = true
            }
        }
    }

    if (wasReported) {
        bounds.clear()
        bounds.addAll(result)
    }
}
