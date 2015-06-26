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
import org.jetbrains.kotlin.codegen.optimization.common.MethodAnalyzer
import org.jetbrains.kotlin.codegen.optimization.common.OptimizationBasicInterpreter
import org.jetbrains.kotlin.codegen.pseudoInsns.PseudoInsn
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode
import org.jetbrains.org.objectweb.asm.tree.JumpInsnNode
import org.jetbrains.org.objectweb.asm.tree.MethodNode
import org.jetbrains.org.objectweb.asm.tree.TryCatchBlockNode
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue
import org.jetbrains.org.objectweb.asm.tree.analysis.Frame
import org.jetbrains.org.objectweb.asm.tree.analysis.Interpreter
import java.util.*

public class FixStackAnalyzer(
        owner: String,
        methodNode: MethodNode,
        val context: FixStackContext
) : MethodAnalyzer<BasicValue>(owner, methodNode, OptimizationBasicInterpreter()) {
    val savedTryCatchStacks = hashMapOf<AbstractInsnNode, List<BasicValue>>()
    var maxExtraStackSize = 0; private set

    protected override fun visitControlFlowEdge(insn: Int, successor: Int): Boolean {
        val insnNode = instructions[insn]
        return !(insnNode is JumpInsnNode && context.breakContinueGotoNodes.contains(insnNode))
    }

    protected override fun newFrame(nLocals: Int, nStack: Int): Frame<BasicValue> =
            FixStackFrame(nLocals, nStack)

    private fun indexOf(node: AbstractInsnNode) = method.instructions.indexOf(node)

    public inner class FixStackFrame(nLocals: Int, nStack: Int) : Frame<BasicValue>(nLocals, nStack) {
        val extraStack = arrayListOf<BasicValue>()

        public override fun init(src: Frame<out BasicValue>): Frame<BasicValue> {
            extraStack.clear()
            extraStack.addAll((src as FixStackFrame).extraStack)
            return super.init(src)
        }

        public override fun clearStack() {
            extraStack.clear()
            super.clearStack()
        }

        public override fun execute(insn: AbstractInsnNode, interpreter: Interpreter<BasicValue>) {
            when {
                PseudoInsn.SAVE_STACK_BEFORE_TRY.isa(insn) -> {
                    val savedValues = getStackContent()
                    savedTryCatchStacks[insn] = savedValues
                    clearStack()
                }
                PseudoInsn.RESTORE_STACK_IN_TRY_CATCH.isa(insn) -> {
                    val saveNode = context.saveStackMarkerForRestoreMarker[insn]
                    val savedValues = savedTryCatchStacks.getOrElse(saveNode) {
                        throw AssertionError("Restore stack for ${indexOf(saveNode)} is unavailable")
                    }
                    pushAll(savedValues)
                }
            }

            super.execute(insn, interpreter)
        }

        public fun getStackContent(): List<BasicValue> {
            val savedStack = arrayListOf<BasicValue>()
            IntRange(0, super.getStackSize() - 1).mapTo(savedStack) { super.getStack(it) }
            savedStack.addAll(extraStack)
            return savedStack
        }

        public override fun push(value: BasicValue) {
            if (super.getStackSize() < getMaxStackSize()) {
                super.push(value)
            }
            else {
                extraStack.add(value)
                maxExtraStackSize = Math.max(maxExtraStackSize, extraStack.size())
            }
        }

        public fun pushAll(values: Collection<BasicValue>) {
            values.forEach { push(it) }
        }

        public override fun pop(): BasicValue {
            if (extraStack.isEmpty()) {
                return super.pop()
            }
            else {
                val top = extraStack[extraStack.size() - 1]
                extraStack.remove(extraStack.size() - 1)
                return top
            }
        }

        public override fun getStack(i: Int): BasicValue {
            if (i < super.getMaxStackSize()) {
                return super.getStack(i)
            }
            else {
                return extraStack[i - super.getMaxStackSize()]
            }
        }
    }
}