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

package org.jetbrains.kotlin.asJava;

import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.checkers.KotlinMultiFileTestWithWithJava;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.test.ConfigurationKind;
import org.jetbrains.kotlin.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.TestJdkKind;
import org.jetbrains.kotlin.utils.ExceptionUtilsKt;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public abstract class AbstractCompilerLightClassTest extends KotlinMultiFileTestWithWithJava<Void, Void> {
    @Override
    @NotNull
    protected CompilerConfiguration createCompilerConfiguration(File javaFilesDir) {
        return KotlinTestUtils.compilerConfigurationForTests(
                ConfigurationKind.ALL,
                TestJdkKind.MOCK_JDK,
                Arrays.asList(KotlinTestUtils.getAnnotationsJar()),
                Arrays.asList(javaFilesDir)
        );
    }

    @NotNull
    @Override
    protected File getKotlinSourceRoot() {
        return createTmpDir("kotlin-src");
    }

    @NotNull
    public static JavaElementFinder createFinder(@NotNull KotlinCoreEnvironment environment) throws IOException {
        // We need to resolve all the files in order too fill in the trace that sits inside LightClassGenerationSupport
        KotlinTestUtils.resolveAllKotlinFiles(environment);

        return JavaElementFinder.getInstance(environment.getProject());
    }

    @Override
    protected void doMultiFileTest(File file, Map<String, ModuleAndDependencies> modules, List<Void> files) throws IOException {
        LightClassTestCommon.INSTANCE.testLightClass(file, new Function1<String, PsiClass>() {
            @Override
            public PsiClass invoke(String s) {
                try {
                    return createFinder(getEnvironment()).findClass(s, GlobalSearchScope.allScope(getEnvironment().getProject()));
                }
                catch (IOException e) {
                    throw ExceptionUtilsKt.rethrow(e);
                }
            }
        }, new Function1<String, String>() {
            @Override
            public String invoke(String s) {
                return LightClassTestCommon.INSTANCE.removeEmptyDefaultImpls(s);
            }
        });
    }

    @Override
    protected Void createTestModule(@NotNull String name) {
        return null;
    }

    @Override
    protected Void createTestFile(Void module, String fileName, String text, Map<String, String> directives) {
        return null;
    }
}
