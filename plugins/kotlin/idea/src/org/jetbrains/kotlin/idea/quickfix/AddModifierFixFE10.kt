// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.psi.isInlineOrValue
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.refactoring.canRefactor
import org.jetbrains.kotlin.idea.refactoring.canRefactorElement
import org.jetbrains.kotlin.idea.util.application.runWriteActionIfPhysical
import org.jetbrains.kotlin.idea.util.collectAllExpectAndActualDeclaration
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.TypeUtils

/** Similar to [AddModifierFix] but with multi-platform support. */
open class AddModifierFixFE10(
    element: KtModifierListOwner,
    modifier: KtModifierKeywordToken
) : AddModifierFix(element, modifier) {
    override fun startInWriteAction(): Boolean = !modifier.isMultiplatformPersistent() ||
            (element?.hasActualModifier() != true && element?.hasExpectModifier() != true)

    override fun invokeImpl(project: Project, editor: Editor?, file: PsiFile) {
        val originalElement = element
        if (originalElement is KtDeclaration && modifier.isMultiplatformPersistent()) {
            val elementsToMutate = originalElement.collectAllExpectAndActualDeclaration(withSelf = true)
            if (elementsToMutate.size > 1 && modifier in modifiersWithWarning) {
                val dialog = MessageDialogBuilder.okCancel(
                    KotlinBundle.message("fix.potentially.broken.inheritance.title"),
                    KotlinBundle.message("fix.potentially.broken.inheritance.message"),
                ).asWarning()

                if (!dialog.ask(project)) return
            }

            runWriteActionIfPhysical(originalElement) {
                for (declaration in elementsToMutate) {
                    invokeOnElement(declaration)
                }
            }
        } else {
            invokeOnElement(originalElement)
        }
    }

    override fun isAvailableImpl(project: Project, editor: Editor?, file: PsiFile): Boolean {
        val element = element ?: return false
        return element.canRefactor()
    }

    companion object : Factory<AddModifierFixFE10> {
        private fun KtModifierKeywordToken.isMultiplatformPersistent(): Boolean =
            this in MODALITY_MODIFIERS || this == INLINE_KEYWORD

        override fun createModifierFix(element: KtModifierListOwner, modifier: KtModifierKeywordToken): AddModifierFixFE10 =
            AddModifierFixFE10(element, modifier)
    }

    object MakeClassOpenFactory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val typeReference = diagnostic.psiElement as KtTypeReference
            val declaration = typeReference.classForRefactor() ?: return null
            if (declaration.isAnnotation() || declaration.isEnum() || declaration.isData() || declaration.isInlineOrValue()) return null
            return AddModifierFixFE10(declaration, OPEN_KEYWORD)
        }
    }

    object AddLateinitFactory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val property = Errors.MUST_BE_INITIALIZED_OR_BE_ABSTRACT.cast(diagnostic).psiElement
            if (!property.isVar) return null

            val descriptor = property.resolveToDescriptorIfAny(BodyResolveMode.FULL) ?: return null
            val type = (descriptor as? PropertyDescriptor)?.type ?: return null

            if (TypeUtils.isNullableType(type)) return null
            if (KotlinBuiltIns.isPrimitiveType(type)) return null

            return AddModifierFixFE10(property, LATEINIT_KEYWORD)
        }
    }
}

fun KtTypeReference.classForRefactor(): KtClass? {
    val bindingContext = analyze(BodyResolveMode.PARTIAL)
    val type = bindingContext[BindingContext.TYPE, this] ?: return null
    val classDescriptor = type.constructor.declarationDescriptor as? ClassDescriptor ?: return null
    val declaration = DescriptorToSourceUtils.descriptorToDeclaration(classDescriptor) as? KtClass ?: return null
    return declaration.takeIf { declaration.canRefactorElement() }
}