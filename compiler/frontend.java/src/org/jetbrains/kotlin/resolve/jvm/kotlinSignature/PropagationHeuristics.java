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

package org.jetbrains.kotlin.resolve.jvm.kotlinSignature;

import com.google.common.collect.Lists;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor;
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor;
import org.jetbrains.kotlin.renderer.DescriptorRenderer;
import org.jetbrains.kotlin.resolve.scopes.MemberScope;
import org.jetbrains.kotlin.types.KotlinType;
import org.jetbrains.kotlin.types.KotlinTypeImpl;
import org.jetbrains.kotlin.types.TypeProjectionImpl;
import org.jetbrains.kotlin.types.Variance;
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker;

import java.util.Arrays;
import java.util.List;

import static org.jetbrains.kotlin.builtins.KotlinBuiltIns.isArray;

// This class contains heuristics for processing corner cases in propagation
class PropagationHeuristics {
    // Checks for case when method returning Super[] is overridden with method returning Sub[]
    static void checkArrayInReturnType(
            @NotNull SignaturesPropagationData data,
            @NotNull KotlinType type,
            @NotNull List<SignaturesPropagationData.TypeAndVariance> typesFromSuper
    ) {
        List<SignaturesPropagationData.TypeAndVariance> arrayTypesFromSuper = ContainerUtil
                .filter(typesFromSuper, new Condition<SignaturesPropagationData.TypeAndVariance>() {
                    @Override
                    public boolean value(SignaturesPropagationData.TypeAndVariance typeAndVariance) {
                        return isArray(typeAndVariance.type);
                    }
                });
        if (isArray(type) && !arrayTypesFromSuper.isEmpty()) {
            assert type.getArguments().size() == 1;
            if (type.getArguments().get(0).getProjectionKind() == Variance.INVARIANT) {
                for (SignaturesPropagationData.TypeAndVariance typeAndVariance : arrayTypesFromSuper) {
                    KotlinType arrayTypeFromSuper = typeAndVariance.type;
                    assert arrayTypeFromSuper.getArguments().size() == 1;
                    KotlinType elementTypeInSuper = arrayTypeFromSuper.getArguments().get(0).getType();
                    KotlinType elementType = type.getArguments().get(0).getType();

                    if (KotlinTypeChecker.DEFAULT.isSubtypeOf(elementType, elementTypeInSuper)
                        && !KotlinTypeChecker.DEFAULT.equalTypes(elementType, elementTypeInSuper)) {
                        KotlinTypeImpl betterTypeInSuper = KotlinTypeImpl.create(
                                arrayTypeFromSuper.getAnnotations(),
                                arrayTypeFromSuper.getConstructor(),
                                arrayTypeFromSuper.isMarkedNullable(),
                                Arrays.asList(new TypeProjectionImpl(Variance.OUT_VARIANCE, elementTypeInSuper)),
                                MemberScope.Companion.empty(elementType.getMemberScope().getContainingDeclaration()));

                        data.reportError("Return type is not a subtype of overridden method. " +
                                         "To fix it, add annotation with Kotlin signature to super method with type "
                                         + DescriptorRenderer.SHORT_NAMES_IN_TYPES.renderType(arrayTypeFromSuper) + " replaced with "
                                         + DescriptorRenderer.SHORT_NAMES_IN_TYPES.renderType(betterTypeInSuper) + " in return type");
                    }
                }
            }
        }
    }

    // Weird workaround for weird case. The sample code below is compiled by javac.
    // In this case, we try to replace "Any" parameter type with "T" to fix substitution principle.
    //
    //    public interface Super<T> {
    //        void foo(T t);
    //    }
    //
    //    public interface Sub<T> extends Super<T> {
    //        void foo(Object o);
    //    }
    //
    // This method is called from SignaturesPropagationData.
    @Nullable
    static ClassifierDescriptor tryToFixOverridingTWithRawType(
            @NotNull SignaturesPropagationData data,
            @NotNull List<SignaturesPropagationData.TypeAndVariance> typesFromSuper
    ) {
        List<TypeParameterDescriptor> typeParameterClassifiersFromSuper = Lists.newArrayList();
        for (SignaturesPropagationData.TypeAndVariance typeFromSuper : typesFromSuper) {
            ClassifierDescriptor classifierFromSuper = typeFromSuper.type.getConstructor().getDeclarationDescriptor();
            if (classifierFromSuper instanceof TypeParameterDescriptor) {
                typeParameterClassifiersFromSuper.add((TypeParameterDescriptor) classifierFromSuper);
            }
        }

        if (!typeParameterClassifiersFromSuper.isEmpty() && typeParameterClassifiersFromSuper.size() == typesFromSuper.size()) {
            for (TypeParameterDescriptor typeParameter : typeParameterClassifiersFromSuper) {
                if (typeParameter.getContainingDeclaration() == data.containingClass) {
                    return typeParameter;
                }
            }
        }

        return null;
    }

    private PropagationHeuristics() {
    }
}
