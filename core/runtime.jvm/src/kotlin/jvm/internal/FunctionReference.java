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

package kotlin.jvm.internal;

import kotlin.jvm.KotlinReflectionNotSupportedError;
import kotlin.reflect.KDeclarationContainer;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.KType;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"unchecked", "NullableProblems"})
public class FunctionReference extends FunctionImpl implements KFunction {
    private final int arity;
    private KFunction reflected;

    public FunctionReference(int arity) {
        this.arity = arity;
    }

    @Override
    public int getArity() {
        return arity;
    }

    // Most of the following methods are copies from CallableReference, since this class cannot inherit from it

    public KDeclarationContainer getOwner() {
        throw new AbstractMethodError();
    }

    @Override
    public String getName() {
        throw new AbstractMethodError();
    }

    public String getSignature() {
        throw new AbstractMethodError();
    }

    @Override
    public List<KParameter> getParameters() {
        return getReflected().getParameters();
    }

    @Override
    public KType getReturnType() {
        return getReflected().getReturnType();
    }

    @Override
    public List<Annotation> getAnnotations() {
        return getReflected().getAnnotations();
    }

    @Override
    public Object call(@NotNull Object... args) {
        return getReflected().call(args);
    }

    @Override
    public Object callBy(@NotNull Map args) {
        return getReflected().callBy(args);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj instanceof FunctionReference) {
            FunctionReference other = (FunctionReference) obj;
            return getOwner().equals(other.getOwner()) &&
                   getName().equals(other.getName()) &&
                   getSignature().equals(other.getSignature());
        }
        if (obj instanceof KFunction) {
            compute();
            return obj.equals(reflected);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (getOwner().hashCode() * 31 + getName().hashCode()) * 31 + getSignature().hashCode();
    }

    @Override
    public String toString() {
        compute();
        if (reflected != this) {
            return reflected.toString();
        }

        // TODO: consider adding the class name to toString() for constructors
        return "<init>".equals(getName())
               ? "constructor" + Reflection.REFLECTION_NOT_AVAILABLE
               : "function " + getName() + Reflection.REFLECTION_NOT_AVAILABLE;
    }

    private void compute() {
        if (reflected == null) {
            reflected = Reflection.function(this);
        }
    }

    public KFunction getReflected() {
        compute();
        if (reflected == this) {
            throw new KotlinReflectionNotSupportedError();
        }
        return reflected;
    }
}
