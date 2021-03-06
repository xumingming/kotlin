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

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil
import com.intellij.codeInspection.*
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import com.intellij.codeInspection.ex.EntryPointsManager
import com.intellij.codeInspection.ex.EntryPointsManagerBase
import com.intellij.codeInspection.ex.EntryPointsManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.PsiSearchHelper.SearchCostResult.*
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.safeDelete.SafeDeleteHandler
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.descriptors.annotations.Annotated
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.findUsages.KotlinFindUsagesHandlerFactory
import org.jetbrains.kotlin.idea.findUsages.handlers.KotlinFindClassUsagesHandler
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.usagesSearch.*
import org.jetbrains.kotlin.idea.util.ProjectRootsUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.utils.singletonOrEmptyList
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JPanel

class UnusedSymbolInspection : AbstractKotlinInspection() {
    companion object {
        private val javaInspection = UnusedDeclarationInspection()

        fun isEntryPoint(declaration: KtNamedDeclaration): Boolean {
            val lightElement: PsiElement? = when (declaration) {
                is KtClassOrObject -> declaration.toLightClass()
                is KtNamedFunction, is KtSecondaryConstructor -> LightClassUtil.getLightClassMethod(declaration as KtFunction)
                is KtProperty -> {
                    // can't rely on light element, check annotation ourselves
                    val descriptor = declaration.descriptor ?: return false
                    val entryPointsManager = EntryPointsManager.getInstance(declaration.getProject()) as EntryPointsManagerBase
                    return checkAnnotatedUsingPatterns(
                            descriptor,
                            entryPointsManager.additionalAnnotations + entryPointsManager.ADDITIONAL_ANNOTATIONS
                    )
                }
                else -> return false
            }
            return lightElement != null && javaInspection.isEntryPoint(lightElement)
        }

        private fun KtProperty.isSerializationImplicitlyUsedField(): Boolean {
            val ownerObject = getNonStrictParentOfType<KtClassOrObject>()
            if (ownerObject is KtObjectDeclaration && ownerObject.isCompanion()) {
                val lightClass = ownerObject.getNonStrictParentOfType<KtClass>()?.toLightClass() ?: return false
                return lightClass.fields.any { it.name == name && HighlightUtil.isSerializationImplicitlyUsedField(it) }
            }
            return false
        }

        private fun KtObjectDeclaration.hasSerializationImplicitlyUsedField(): Boolean {
            return declarations.any { it is KtProperty && it.isSerializationImplicitlyUsedField() }
        }

        // variation of IDEA's AnnotationUtil.checkAnnotatedUsingPatterns()
        private fun checkAnnotatedUsingPatterns(annotated: Annotated, annotationPatterns: Collection<String>): Boolean {
            val annotationsPresent = annotated.annotations
                    .map { it.type }
                    .filter { !it.isError }
                    .mapNotNull { it.constructor.declarationDescriptor?.let { DescriptorUtils.getFqName(it).asString() } }

            if (annotationsPresent.isEmpty()) return false

            for (pattern in annotationPatterns) {
                val hasAnnotation = if (pattern.endsWith(".*")) {
                    annotationsPresent.any { it.startsWith(pattern.dropLast(1)) }
                } else {
                    pattern in annotationsPresent
                }
                if (hasAnnotation) return true
            }

            return false
        }

    }

    override fun runForWholeFile() = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : KtVisitorVoid() {
            private fun createQuickFix(declaration: KtNamedDeclaration): LocalQuickFix {
                return object : LocalQuickFix {
                    override fun getName() = QuickFixBundle.message("safe.delete.text", declaration.name)

                    override fun getFamilyName() = "Safe delete"

                    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                        if (!FileModificationService.getInstance().prepareFileForWrite(declaration.containingFile)) return
                        SafeDeleteHandler.invoke(project, arrayOf(declaration), false)
                    }
                }
            }

            override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
                val messageKey = when (declaration) {
                    is KtClass -> "unused.class"
                    is KtObjectDeclaration -> "unused.object"
                    is KtNamedFunction -> "unused.function"
                    is KtProperty, is KtParameter -> "unused.property"
                    is KtTypeParameter -> "unused.type.parameter"
                    else -> return
                }

                if (!ProjectRootsUtil.isInProjectSource(declaration)) return

                // Simple PSI-based checks
                val isCompanionObject = declaration is KtObjectDeclaration && declaration.isCompanion()
                if (declaration.name == null) return
                if (declaration is KtEnumEntry) return
                if (declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return
                if (declaration is KtProperty && declaration.isLocal) return
                if (declaration is KtParameter && (declaration.getParent()?.parent !is KtPrimaryConstructor || !declaration.hasValOrVar())) return
                if (declaration is KtNamedFunction && isConventionalName(declaration)) return

                // More expensive, resolve-based checks
                if (declaration.resolveToDescriptorIfAny() == null) return
                if (isEntryPoint(declaration)) return
                if (declaration is KtProperty && declaration.isSerializationImplicitlyUsedField()) return
                if (isCompanionObject && (declaration as KtObjectDeclaration).hasSerializationImplicitlyUsedField()) return
                // properties can be referred by component1/component2, which is too expensive to search, don't mark them as unused
                if (declaration is KtParameter && declaration.dataClassComponentFunction() != null) return

                // Main checks: finding reference usages && text usages
                if (hasNonTrivialUsages(declaration)) return
                if (declaration is KtClassOrObject && classOrObjectHasTextUsages(declaration)) return

                val (inspectionTarget, textRange) = if (isCompanionObject && declaration.nameIdentifier == null) {
                    val objectKeyword = (declaration as KtObjectDeclaration).getObjectKeyword()
                    Pair(declaration, TextRange(0, objectKeyword.startOffsetInParent + objectKeyword.textLength))
                } else {
                    Pair(declaration.nameIdentifier!!, null)
                }

                val problemDescriptor = holder.manager.createProblemDescriptor(
                        inspectionTarget,
                        textRange,
                        KotlinBundle.message(messageKey, declaration.name),
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                        true,
                        createQuickFix(declaration)
                )

                holder.registerProblem(problemDescriptor)
            }
        }
    }

    override val suppressionKey: String get() = "unused"

    private fun classOrObjectHasTextUsages(classOrObject: KtClassOrObject): Boolean {
        var hasTextUsages = false

        // Finding text usages
        if (classOrObject.useScope is GlobalSearchScope) {
            val findClassUsagesHandler = KotlinFindClassUsagesHandler(classOrObject, KotlinFindUsagesHandlerFactory(classOrObject.project))
            findClassUsagesHandler.processUsagesInText(
                    classOrObject,
                    { hasTextUsages = true; false },
                    GlobalSearchScope.projectScope(classOrObject.project)
            )
        }

        return hasTextUsages
    }

    private fun isConventionalName(namedDeclaration: KtNamedDeclaration): Boolean {
        val name = namedDeclaration.nameAsName
        return name!!.getOperationSymbolsToSearch().isNotEmpty() || name == OperatorNameConventions.INVOKE
    }

    private fun hasNonTrivialUsages(declaration: KtNamedDeclaration): Boolean {
        val psiSearchHelper = PsiSearchHelper.SERVICE.getInstance(declaration.project)

        val useScope = declaration.useScope
        if (useScope is GlobalSearchScope) {
            var zeroOccurrences = true

            for (name in listOf(declaration.name) + declaration.getAccessorNames() + declaration.getClassNameForCompanionObject().singletonOrEmptyList()) {
                assert(name != null) { "Name is null for " + declaration.getElementTextWithContext() }
                when (psiSearchHelper.isCheapEnoughToSearch(name!!, useScope, null, null)) {
                    ZERO_OCCURRENCES -> {} // go on, check other names
                    FEW_OCCURRENCES -> zeroOccurrences = false
                    TOO_MANY_OCCURRENCES -> return true // searching usages is too expensive; behave like it is used
                }
            }

            if (zeroOccurrences) {
                if (declaration is KtObjectDeclaration && declaration.isCompanion()) {
                    // go on: companion object can be used only in containing class
                }
                else {
                    return false
                }
            }
        }

        return (declaration is KtObjectDeclaration && declaration.isCompanion() &&
                declaration.getBody()?.declarations?.isNotEmpty() == true) ||
               hasReferences(declaration, useScope) ||
               hasOverrides(declaration, useScope)

    }

    private fun hasReferences(declaration: KtNamedDeclaration, useScope: SearchScope): Boolean {
        return !ReferencesSearch.search(declaration, useScope).forEach(fun (ref: PsiReference): Boolean {
            if (declaration.isAncestor(ref.element)) return true // usages inside element's declaration are not counted

            if (ref.element.parent is KtValueArgumentName) return true // usage of parameter in form of named argument is not counted

            val import = ref.element.getParentOfType<KtImportDirective>(false)
            if (import != null) {
                // check if we import member(s) from object or enum and search for their usages
                if (declaration is KtObjectDeclaration || (declaration is KtClass && declaration.isEnum())) {
                    if (import.isAllUnder) {
                        val importedFrom = import.importedReference?.getQualifiedElementSelector()?.mainReference?.resolve()
                                                   as? KtClassOrObject ?: return true
                        return importedFrom.declarations.none { it is KtNamedDeclaration && hasNonTrivialUsages(it) }
                    }
                    else {
                        if (import.importedFqName != declaration.fqName) {
                            val importedDeclaration = import.importedReference?.getQualifiedElementSelector()?.mainReference?.resolve() as? KtNamedDeclaration
                            return importedDeclaration == null || !hasNonTrivialUsages(importedDeclaration)
                        }
                    }
                }
                return true
            }

            return false
        })
    }

    private fun hasOverrides(declaration: KtNamedDeclaration, useScope: SearchScope): Boolean {
        return DefinitionsScopedSearch.search(declaration, useScope).findFirst() != null
    }

    override fun createOptionsPanel(): JComponent? {
        val panel = JPanel(GridBagLayout())
        panel.add(
                EntryPointsManagerImpl.createConfigureAnnotationsButton(),
                GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, Insets(0, 0, 0, 0), 0, 0)
        )
        return panel
    }
}
