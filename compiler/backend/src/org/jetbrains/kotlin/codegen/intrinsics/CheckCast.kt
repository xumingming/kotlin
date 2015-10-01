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

package org.jetbrains.kotlin.codegen.intrinsics

import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.types.JetType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode
import org.jetbrains.org.objectweb.asm.tree.InsnList
import org.jetbrains.org.objectweb.asm.tree.MethodInsnNode
import org.jetbrains.org.objectweb.asm.tree.TypeInsnNode

object CheckCast {
    private val TYPE_INTRINSICS_CLASS = "kotlin/jvm/internal/TypeIntrinsics"

    private val CHECKCAST_METHOD_NAME = hashMapOf(
            "kotlin.MutableIterator" to "asMutableIterator",
            "kotlin.MutableIterable" to "asMutableIterable",
            "kotlin.MutableCollection" to "asMutableCollection",
            "kotlin.MutableList" to "asMutableList",
            "kotlin.MutableListIterator" to "asMutableListIterator",
            "kotlin.MutableSet" to "asMutableSet",
            "kotlin.MutableMap" to "asMutableMap",
            "kotlin.MutableMap.MutableEntry" to "asMutableMapEntry"
    )

    private val SAFE_CHECKCAST_METHOD_NAME = hashMapOf(
            "kotlin.MutableIterator" to "safeAsMutableIterator",
            "kotlin.MutableIterable" to "safeAsMutableIterable",
            "kotlin.MutableCollection" to "safeAsMutableCollection",
            "kotlin.MutableList" to "safeAsMutableList",
            "kotlin.MutableListIterator" to "safeAsMutableListIterator",
            "kotlin.MutableSet" to "safeAsMutableSet",
            "kotlin.MutableMap" to "safeAsMutableMap",
            "kotlin.MutableMap.MutableEntry" to "safeAsMutableMapEntry"
    )

    public @JvmStatic fun hasSafeCheckcastIntrinsic(jetType: JetType): Boolean =
            getCheckcastIntrinsicMethodName(jetType, true) != null

    public @JvmStatic fun checkcast(v: InstructionAdapter, jetType: JetType, boxedAsmType: Type, safe: Boolean) {
        val intrinsicMethodName = getCheckcastIntrinsicMethodName(jetType, safe)
        if (intrinsicMethodName == null) {
            v.checkcast(boxedAsmType)
        }
        else {
            val signature = getCheckcastIntrinsicMethodDescriptor(boxedAsmType)
            v.invokestatic(TYPE_INTRINSICS_CLASS, intrinsicMethodName, signature, false)
        }
    }

    public @JvmStatic fun checkcast(checkcastInsn: TypeInsnNode, instructions: InsnList, jetType: JetType, asmType: Type, safe: Boolean) {
        val intrinsicMethodName = getCheckcastIntrinsicMethodName(jetType, safe)
        if (intrinsicMethodName == null) {
            checkcastInsn.desc = asmType.internalName
        }
        else {
            val signature = getCheckcastIntrinsicMethodDescriptor(asmType)
            val intrinsicNode = MethodInsnNode(Opcodes.INVOKESTATIC, TYPE_INTRINSICS_CLASS, intrinsicMethodName, signature, false)
            instructions.insertBefore(checkcastInsn, intrinsicNode)
            instructions.remove(checkcastInsn)
            if (safe) {
                removeInstanceOfCheck(intrinsicNode, instructions)
            }
        }
    }

    private fun removeInstanceOfCheck(intrinsicNode: MethodInsnNode, instructions: InsnList) {
        // After removing type parameter reification markers the code generated for 'as?' looks like:
        //   1     DUP
        //   2     INVOKESTATIC kotlin/jvm/internal/TypeIntrinsics.(...) <-- intrinsic for 'is'
        //   3     IFNE LA
        //   4     POP
        //   5     ACONST_NULL
        //   6  LA:
        //          <special instruction for safe-cast type parameter>
        //          <reify marker for safe-cast>
        //   7     INVOKESTATIC kotlin/jvm/internal/TypeIntrinsics.(...) <-- intrinsic for 'as?'
        // Intrinsic for 'as?' performs 'is'-related checks, so we should remove instructions 1..6.

        if (intrinsicNode.opcode != Opcodes.INVOKESTATIC && intrinsicNode.owner != TYPE_INTRINSICS_CLASS) return

        val reifyMarkerInsn = intrinsicNode.previous ?: return
        val typeParamInsn = reifyMarkerInsn.previous ?: return

        val insn6 = typeParamInsn.previous ?: return
        val insn5 = insn6.previous ?: return
        val insn4 = insn5.previous ?: return
        val insn3 = insn4.previous ?: return
        val insn2 = insn3.previous ?: return
        val insn1 = insn2.previous ?: return

        if (insn1.opcode != Opcodes.DUP) return
        if (insn2.opcode != Opcodes.INVOKESTATIC && (insn2 !is MethodInsnNode || insn2.owner != TYPE_INTRINSICS_CLASS)) return
        if (insn3.opcode != Opcodes.IFNE) return
        if (insn4.opcode != Opcodes.POP) return
        if (insn5.opcode != Opcodes.ACONST_NULL) return
        if (insn6.type != AbstractInsnNode.LABEL) return

        instructions.remove(insn1)
        instructions.remove(insn2)
        instructions.remove(insn3)
        instructions.remove(insn4)
        instructions.remove(insn5)
        instructions.remove(insn6)
    }

    private fun getCheckcastIntrinsicMethodName(jetType: JetType, safe: Boolean): String? {
        val classDescriptor = TypeUtils.getClassDescriptor(jetType) ?: return null
        val classFqName = DescriptorUtils.getFqName(classDescriptor).asString()
        return if (safe) SAFE_CHECKCAST_METHOD_NAME[classFqName] else CHECKCAST_METHOD_NAME[classFqName]
    }

    private fun getCheckcastIntrinsicMethodDescriptor(asmType: Type): String =
            Type.getMethodDescriptor(asmType, Type.getObjectType("java/lang/Object"));

}