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

import com.intellij.util.containers.Stack
import org.jetbrains.kotlin.codegen.inline.InlineCodegenUtil
import org.jetbrains.kotlin.codegen.optimization.fixStack.forEachPseudoInsn
import org.jetbrains.kotlin.codegen.pseudoInsns.PseudoInsn
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.tree.*
import kotlin.properties.Delegates

internal class FixStackContext(val methodNode: MethodNode) {
    val breakContinueGotoNodes = linkedSetOf<JumpInsnNode>()
    val fakeAlwaysTrueIfeqMarkers = arrayListOf<AbstractInsnNode>()
    val fakeAlwaysFalseIfeqMarkers = arrayListOf<AbstractInsnNode>()

    val saveStackBeforeTryMarkers = arrayListOf<AbstractInsnNode>()
    val restoreStackInTryCatchMarkers = arrayListOf<AbstractInsnNode>()

    val saveStackNodesForTryStartLabels = hashMapOf<LabelNode, AbstractInsnNode>()
    val saveStackMarkerForRestoreMarker = hashMapOf<AbstractInsnNode, AbstractInsnNode>()

    init {
        parsePseudoInsns()
    }

    private fun parsePseudoInsns() {
        breakContinueGotoNodes.clear()
        fakeAlwaysTrueIfeqMarkers.clear()
        fakeAlwaysFalseIfeqMarkers.clear()

        saveStackBeforeTryMarkers.clear()
        restoreStackInTryCatchMarkers.clear()
        saveStackNodesForTryStartLabels.clear()
        saveStackMarkerForRestoreMarker.clear()

        methodNode.instructions.forEachPseudoInsn { pseudoInsn, insnNode ->
            when (pseudoInsn) {
                PseudoInsn.FIX_STACK_BEFORE_JUMP ->
                    visitFixStackBeforeJump(insnNode, pseudoInsn)
                PseudoInsn.FAKE_ALWAYS_TRUE_IFEQ ->
                    visitFakeAlwaysTrueIfeq(insnNode, pseudoInsn)
                PseudoInsn.FAKE_ALWAYS_FALSE_IFEQ ->
                    visitFakeAlwaysFalseIfeq(insnNode, pseudoInsn)
                PseudoInsn.SAVE_STACK_BEFORE_TRY ->
                    visitSaveStackBeforeTry(insnNode, pseudoInsn)
                PseudoInsn.RESTORE_STACK_IN_TRY_CATCH ->
                    visitRestoreStackInTryCatch(insnNode, pseudoInsn)
                else -> {}
            }
        }
    }

    private fun visitFixStackBeforeJump(insnNode: AbstractInsnNode, pseudoInsn: PseudoInsn) {
        val next = insnNode.getNext()
        assert(next.getOpcode() == Opcodes.GOTO, "$pseudoInsn should be followed by GOTO")
        breakContinueGotoNodes.add(next as JumpInsnNode)
    }

    private fun visitFakeAlwaysTrueIfeq(insnNode: AbstractInsnNode, pseudoInsn: PseudoInsn) {
        assert(insnNode.getNext().getOpcode() == Opcodes.IFEQ, "$pseudoInsn should be followed by IFEQ")
        fakeAlwaysTrueIfeqMarkers.add(insnNode)
    }

    private fun visitFakeAlwaysFalseIfeq(insnNode: AbstractInsnNode, pseudoInsn: PseudoInsn) {
        assert(insnNode.getNext().getOpcode() == Opcodes.IFEQ, "$pseudoInsn should be followed by IFEQ")
        fakeAlwaysFalseIfeqMarkers.add(insnNode)
    }

    private fun visitSaveStackBeforeTry(insnNode: AbstractInsnNode, pseudoInsn: PseudoInsn) {
        val tryStartLabel = insnNode.getNext()
        assert(tryStartLabel is LabelNode, "$pseudoInsn should be followed by a label")
        saveStackNodesForTryStartLabels[tryStartLabel as LabelNode] = insnNode
        saveStackBeforeTryMarkers.add(insnNode)
    }

    private fun indexOf(node: AbstractInsnNode) = methodNode.instructions.indexOf(node)
    private fun TryCatchBlockNode.debugString() = "TCB<${indexOf(start)}, ${indexOf(end)}, ${indexOf(handler)}>"

    private fun visitRestoreStackInTryCatch(insnNode: AbstractInsnNode, pseudoInsn: PseudoInsn) {
        val restoreLabel = insnNode.getPrevious()?.getPrevious()
        if (restoreLabel !is LabelNode) {
            throw AssertionError("$pseudoInsn should be preceded by a catch block prefix")
        }
        restoreStackInTryCatchMarkers.add(insnNode)
        var saveNode: AbstractInsnNode? = null
        for (tcb in methodNode.tryCatchBlocks) {
            if (restoreLabel == tcb.start || restoreLabel == tcb.handler) {
                saveNode = saveStackNodesForTryStartLabels[tcb.start]
                if (saveNode != null) break
            }
        }
        if (saveNode == null) {
            throw AssertionError("$pseudoInsn at ${indexOf(insnNode)}, in handler ${indexOf(restoreLabel)} is not matched")
        }
        saveStackMarkerForRestoreMarker[insnNode] = saveNode
    }

    fun hasAnyMarkers(): Boolean =
            breakContinueGotoNodes.isNotEmpty() ||
            fakeAlwaysTrueIfeqMarkers.isNotEmpty() ||
            fakeAlwaysFalseIfeqMarkers.isNotEmpty() ||
            saveStackBeforeTryMarkers.isNotEmpty() ||
            restoreStackInTryCatchMarkers.isNotEmpty()

    fun isAnalysisRequired(): Boolean =
            breakContinueGotoNodes.isNotEmpty() ||
            saveStackBeforeTryMarkers.isNotEmpty() ||
            restoreStackInTryCatchMarkers.isNotEmpty()
}