// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.factories

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.CallableImportCandidatesProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportCandidate
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportContext
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportPositionTypeAndReceiver
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.util.OperatorNameConventions

internal object IteratorImportQuickFixFactory : AbstractImportQuickFixFactory() {
    override fun detectPositionContext(diagnostic: KaDiagnosticWithPsi<*>): ImportContext? =
        when (diagnostic) {
            is KaFirDiagnostic.IteratorMissing,
            is KaFirDiagnostic.IteratorAmbiguity -> {
                val iteratedExpression = diagnostic.psi as? KtExpression ?: return null
                ImportContext(iteratedExpression, ImportPositionTypeAndReceiver.OperatorCall(iteratedExpression))
            }

            else -> null
        }

    override fun provideUnresolvedNames(diagnostic: KaDiagnosticWithPsi<*>, importPositionContext: ImportContext): Set<Name> =
        setOf(OperatorNameConventions.ITERATOR)

    override fun KaSession.provideImportCandidates(
        unresolvedName: Name,
        importPositionContext: ImportContext,
        indexProvider: KtSymbolFromIndexProvider,
    ): List<ImportCandidate> {
        val provider = CallableImportCandidatesProvider(importPositionContext)
        return provider.collectCandidates(unresolvedName, indexProvider)
    }
}
