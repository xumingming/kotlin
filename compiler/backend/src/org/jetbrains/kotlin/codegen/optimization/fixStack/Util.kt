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

import org.jetbrains.kotlin.codegen.inline.InlineCodegenUtil
import org.jetbrains.kotlin.codegen.optimization.common.InsnSequence
import org.jetbrains.kotlin.codegen.optimization.common.MethodAnalyzer
import org.jetbrains.kotlin.codegen.optimization.common.OptimizationBasicInterpreter
import org.jetbrains.kotlin.codegen.optimization.common.getStackValuesStartingFrom
import org.jetbrains.kotlin.codegen.optimization.transformer.MethodTransformer
import org.jetbrains.kotlin.codegen.pseudoInsns.PseudoInsn
import org.jetbrains.kotlin.codegen.pseudoInsns.PseudoInsn
import org.jetbrains.kotlin.codegen.pseudoInsns.parsePseudoInsnOrNull
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.tree.*
import org.jetbrains.org.objectweb.asm.tree.analysis.*
import java.util.*
import kotlin.properties.Delegates
import kotlin.test.assertEquals

public inline fun InsnList.forEachPseudoInsn(block: (PseudoInsn, AbstractInsnNode) -> Unit) {
    InsnSequence(this).forEach { insn ->
        parsePseudoInsnOrNull(insn)?.let { block(it, insn) }
    }
}

public inline fun InsnList.forEachInlineMarker(block: (String, AbstractInsnNode) -> Unit) {
    InsnSequence(this).forEach { insn ->
        if (InlineCodegenUtil.isInlineMarker(insn)) {
            block((insn as MethodInsnNode).name, insn)
        }
    }
}
