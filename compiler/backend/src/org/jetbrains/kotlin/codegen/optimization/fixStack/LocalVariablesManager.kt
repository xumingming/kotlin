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

package org.jetbrains.kotlin.codegen.optimization.fixStack

import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode
import org.jetbrains.org.objectweb.asm.tree.MethodNode
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue

internal class LocalVariablesManager(val context: FixStackContext, val methodNode: MethodNode) {
    private class AllocatedHandle(val savedStackDescriptor: SavedStackDescriptor, var expectedRestoreMarkers: Int) {
        fun isFullyEmitted(): Boolean =
                expectedRestoreMarkers == 0

        fun markRestoreNodeEmitted() {
            assert(expectedRestoreMarkers > 0, "Emitted more restore markers than expected for $savedStackDescriptor")
        }
    }

    private val initialMaxLocals = methodNode.maxLocals
    private val allocatedHandles = hashMapOf<AbstractInsnNode, AllocatedHandle>()

    private fun updateMaxLocals(newValue: Int) {
        methodNode.maxLocals = Math.max(methodNode.maxLocals, newValue)
    }

    fun allocateVariablesForSaveStackMarker(saveStackMarker: AbstractInsnNode, savedStackValues: List<BasicValue>): SavedStackDescriptor {
        val allocatedHandle = allocatedHandles.getOrPut(saveStackMarker) {
            val restoreStackMarkers = context.restoreStackMarkersForSaveMarker[saveStackMarker]
            val firstUnusedLocalVarIndex = getFirstUnusedLocalVariableIndex()
            val savedStackDescriptor = SavedStackDescriptor(savedStackValues, firstUnusedLocalVarIndex)
            updateMaxLocals(savedStackDescriptor.firstUnusedLocalVarIndex)
            AllocatedHandle(savedStackDescriptor, restoreStackMarkers.size())
        }
        return allocatedHandle.savedStackDescriptor
    }

    fun getSavedStackDescriptorOrNull(restoreStackMarker: AbstractInsnNode): SavedStackDescriptor? {
        val saveStackMarker = context.saveStackMarkerForRestoreMarker[restoreStackMarker]
        return allocatedHandles[saveStackMarker]?.savedStackDescriptor
    }

    private fun getFirstUnusedLocalVariableIndex(): Int =
            allocatedHandles.values().fold(initialMaxLocals) {
                index, tcb -> Math.max(index, tcb.savedStackDescriptor.firstUnusedLocalVarIndex)
            }

    fun markRestoreStackMarkerEmitted(restoreStackMarker: AbstractInsnNode) {
        val saveStackMarker = context.saveStackMarkerForRestoreMarker[restoreStackMarker]
        val allocatedHandle = allocatedHandles[saveStackMarker]
        allocatedHandle.markRestoreNodeEmitted()
        if (allocatedHandle.isFullyEmitted()) {
            allocatedHandles.remove(saveStackMarker)
        }
    }

    fun allocateVariablesForBeforeInlineMarker(beforeInlineMarker: AbstractInsnNode, savedStackValues: List<BasicValue>): SavedStackDescriptor {
        val allocatedHandle = allocatedHandles.getOrPut(beforeInlineMarker) {
            val firstUnusedLocalVarIndex = getFirstUnusedLocalVariableIndex()
            val savedStackDescriptor = SavedStackDescriptor(savedStackValues, firstUnusedLocalVarIndex)
            updateMaxLocals(savedStackDescriptor.firstUnusedLocalVarIndex)
            AllocatedHandle(savedStackDescriptor, 1)
        }
        return allocatedHandle.savedStackDescriptor
    }

    fun getBeforeInlineDescriptor(afterInlineMarker: AbstractInsnNode): SavedStackDescriptor {
        val beforeInlineMarker = context.openingInlineMethodMarker[afterInlineMarker]
        return allocatedHandles[beforeInlineMarker].savedStackDescriptor
    }

    fun markAfterInlineMarkerEmitted(afterInlineMarker: AbstractInsnNode) {
        val beforeInlineMarker = context.openingInlineMethodMarker[afterInlineMarker]
        val allocatedHandle = allocatedHandles[beforeInlineMarker]
        allocatedHandle.markRestoreNodeEmitted()
        if (allocatedHandle.isFullyEmitted()) {
            allocatedHandles.remove(beforeInlineMarker)
        }
    }

    fun createReturnValueVariable(returnValue: BasicValue): Int {
        val returnValueIndex = getFirstUnusedLocalVariableIndex()
        updateMaxLocals(returnValueIndex + returnValue.getSize())
        return returnValueIndex
    }
}