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
import org.jetbrains.kotlin.codegen.optimization.common.InsnSequence
import org.jetbrains.kotlin.codegen.optimization.common.MethodAnalyzer
import org.jetbrains.kotlin.codegen.optimization.common.OptimizationBasicInterpreter
import org.jetbrains.kotlin.codegen.optimization.fixStack.forEachPseudoInsn
import org.jetbrains.kotlin.codegen.optimization.transformer.MethodTransformer
import org.jetbrains.kotlin.codegen.pseudoInsns.PseudoInsn
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.tree.*
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue
import org.jetbrains.org.objectweb.asm.tree.analysis.Frame
import org.jetbrains.org.objectweb.asm.tree.analysis.Interpreter
import java.util.*
import kotlin.properties.Delegates

public class FixStackTransformer : MethodTransformer() {
    private var context: FixStackContext by Delegates.notNull()

    public override fun transform(internalClassName: String, methodNode: MethodNode) {
        context = FixStackContext(methodNode)

        if (!context.hasAnyMarkers()) return

        if (context.isAnalysisRequired()) {
            val analyzer = FixStackAnalyzer(internalClassName, methodNode, context)
            analyzer.analyze()

            methodNode.maxStack = methodNode.maxStack + analyzer.maxExtraStackSize

            val actions = arrayListOf<() -> Unit>()

            transformBreakContinueGotos(methodNode, context, actions, analyzer)
            transformFixStackOnTryCatchMarkers(methodNode, context, actions, analyzer)

            actions.forEach { it() }

            preventSaveStackBeforeInlineMethodIfNeeded(analyzer, methodNode)
        }

        for (marker in context.fakeAlwaysTrueIfeqMarkers) {
            replaceAlwaysTrueIfeqWithGoto(methodNode, marker)
        }

        for (marker in context.fakeAlwaysFalseIfeqMarkers) {
            removeAlwaysFalseIfeq(methodNode, marker)
        }
    }

    private fun preventSaveStackBeforeInlineMethodIfNeeded(analyzer: FixStackAnalyzer, methodNode: MethodNode) {
        if (hasInlineMethodMarkers(methodNode) && hasNonEmptySavedStacks(analyzer)) {
            // Prevent SaveStackBeforeInlineMethodTransformer from messing with our stacks.
            // We can't do it ourselves here, because some other optimizations will stop working.
            // We can't fix stacks after other optimizations, since they depend on stack data flow analysis.
            // Insert non-balanced "beforeInlineCall" after first method node (which is a LabelNode after label normalization).
            with(methodNode.instructions) {
                insert(getFirst(),
                       MethodInsnNode(Opcodes.INVOKESTATIC,
                                      InlineCodegenUtil.INLINE_MARKER_CLASS_NAME,
                                      InlineCodegenUtil.INLINE_MARKER_BEFORE_METHOD_NAME,
                                      "()V", false))
            }
        }
    }

    private fun hasInlineMethodMarkers(methodNode: MethodNode) =
            InsnSequence(methodNode.instructions) any { InlineCodegenUtil.isInlineMarker(it) }

    private fun hasNonEmptySavedStacks(analyzer: FixStackAnalyzer): Boolean =
            analyzer.savedTryCatchStacks.values() any { it.isNotEmpty() }

    private fun transformBreakContinueGotos(methodNode: MethodNode, fixStackContext: FixStackContext, actions: MutableList<() -> Unit>, analyzer: FixStackAnalyzer) {
        for (gotoNode in fixStackContext.breakContinueGotoNodes) {
            val gotoIndex = methodNode.instructions.indexOf(gotoNode)
            val labelIndex = methodNode.instructions.indexOf(gotoNode.label)

            val DEAD_CODE = -1 // Stack size is always non-negative
            val actualStackSize = analyzer.frames[gotoIndex]?.getStackSize() ?: DEAD_CODE
            val expectedStackSize = analyzer.frames[labelIndex]?.getStackSize() ?: DEAD_CODE

            if (actualStackSize != DEAD_CODE && expectedStackSize != DEAD_CODE) {
                assert(expectedStackSize <= actualStackSize,
                       "Label at $labelIndex, jump at $gotoIndex: stack underflow: $expectedStackSize > $actualStackSize")
                val frame = analyzer.frames[gotoIndex]!!
                actions.add({ replaceMarkerWithPops(methodNode, gotoNode.getPrevious(), expectedStackSize, frame) })
            }
            else if (actualStackSize != DEAD_CODE && expectedStackSize == DEAD_CODE) {
                throw AssertionError("Live jump $gotoIndex to dead label $labelIndex")
            }
            else {
                val marker = gotoNode.getPrevious()
                actions.add({ methodNode.instructions.remove(marker) })
            }
        }
    }

    private fun removeAlwaysFalseIfeq(methodNode: MethodNode, nodeToReplace: AbstractInsnNode) {
        with (methodNode.instructions) {
            remove(nodeToReplace.getNext())
            remove(nodeToReplace)
        }
    }

    private fun replaceAlwaysTrueIfeqWithGoto(methodNode: MethodNode, nodeToReplace: AbstractInsnNode) {
        with (methodNode.instructions) {
            val next = nodeToReplace.getNext() as JumpInsnNode
            insertBefore(nodeToReplace, JumpInsnNode(Opcodes.GOTO, next.label))
            remove(nodeToReplace)
            remove(next)
        }
    }

    private fun replaceMarkerWithPops(methodNode: MethodNode, nodeToReplace: AbstractInsnNode, expectedStackSize: Int, frame: Frame<BasicValue>) {
        with (methodNode.instructions) {
            while (frame.getStackSize() > expectedStackSize) {
                val top = frame.pop()
                insertBefore(nodeToReplace, getPopInstruction(top))
            }
            remove(nodeToReplace)
        }
    }

    private fun getPopInstruction(top: BasicValue) =
            InsnNode(when (top.getSize()) {
                         1 -> Opcodes.POP
                         2 -> Opcodes.POP2
                         else -> throw AssertionError("Unexpected value type size")
                     })

    private fun transformFixStackOnTryCatchMarkers(
            methodNode: MethodNode,
            context: FixStackContext,
            actions: MutableList<() -> Unit>,
            analyzer: FixStackAnalyzer
    ) {
        val savedStackDescriptors = hashMapOf<AbstractInsnNode, SavedStackDescriptor>()
        var firstUnusedLocalIndex = methodNode.maxLocals

        for (saveStackMarker in context.saveStackBeforeTryMarkers) {
            val savedStackValues = analyzer.savedTryCatchStacks[saveStackMarker]
            if (savedStackValues != null) {
                val savedStackDescriptor = SavedStackDescriptor(savedStackValues, firstUnusedLocalIndex)
                savedStackDescriptors[saveStackMarker] = savedStackDescriptor
                firstUnusedLocalIndex += savedStackValues.size()
                actions.add({ saveStackAtMarker(methodNode, saveStackMarker, savedStackDescriptor) })
            }
            else {
                // marker is dead code
                actions.add({ methodNode.instructions.remove(saveStackMarker) })
            }
        }

        for (restoreStackMarker in context.restoreStackInTryCatchMarkers) {
            val saveStackMarker = context.saveStackMarkerForRestoreMarker[restoreStackMarker]
            val savedStackDescriptor = savedStackDescriptors[saveStackMarker]
            if (savedStackDescriptor != null) {
                actions.add({ restoreStackAtMarker(methodNode, restoreStackMarker, savedStackDescriptor) })
            }
            else {
                // marker is dead code
                actions.add({ methodNode.instructions.remove(restoreStackMarker) })
            }
        }

        methodNode.maxLocals = firstUnusedLocalIndex
    }

    private fun saveStackAtMarker(
            methodNode: MethodNode,
            saveStackMarker: AbstractInsnNode,
            savedStackDescriptor: SavedStackDescriptor
    ) {
        with(methodNode.instructions) {
            generateStoreInstructions(saveStackMarker, savedStackDescriptor)
            remove(saveStackMarker)
        }
    }

    private fun restoreStackAtMarker(
            methodNode: MethodNode,
            restoreStackMarker: AbstractInsnNode,
            savedStackDescriptor: SavedStackDescriptor
    ) {
        with(methodNode.instructions) {
            generateLoadInstructions(restoreStackMarker, savedStackDescriptor)
            remove(restoreStackMarker)
        }
    }

    private fun InsnList.generateLoadInstructions(location: AbstractInsnNode, savedStackDescriptor: SavedStackDescriptor) {
        var localVarIndex = savedStackDescriptor.firstLocalVarIndex
        for (value in savedStackDescriptor.savedValues) {
            insertBefore(location,
                         VarInsnNode(value.getType().getOpcode(Opcodes.ILOAD), localVarIndex))
            localVarIndex++
        }
    }

    private fun InsnList.generateStoreInstructions(location: AbstractInsnNode, savedStackDescriptor: SavedStackDescriptor) {
        var localVarIndex = savedStackDescriptor.firstLocalVarIndex
        for (value in savedStackDescriptor.savedValues) {
            insertBefore(location,
                         VarInsnNode(value.getType().getOpcode(Opcodes.ISTORE), localVarIndex))
            localVarIndex++
        }
    }

    private class SavedStackDescriptor(val savedValues: List<BasicValue>, val firstLocalVarIndex: Int) {
        public override fun toString(): String =
                "@$firstLocalVarIndex: [$savedValues] "
    }

}