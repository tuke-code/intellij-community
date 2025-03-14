// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.shared.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.ui.Messages
import org.jetbrains.kotlin.idea.configuration.*
import org.jetbrains.kotlin.idea.jvm.shared.KotlinJvmBundle
import org.jetbrains.kotlin.platform.jvm.isJvm

class ConfigureKotlinInProjectAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val progressTitle = KotlinJvmBundle.message("lookup.project.configurators.progress.text")
        val (modules, configurators) = ActionUtil.underModalProgress(project, progressTitle) {
            val modules = getConfigurableModules(project)
            if (modules.all(::isModuleConfigured)) {
                return@underModalProgress modules to emptyList<KotlinProjectConfigurator>()
            }
            val configurators = getAbleToRunConfigurators(project).filter {
                it.targetPlatform.isJvm()
            }
            modules to configurators
        }

        if (modules.all(::isModuleConfigured)) {
            Messages.showInfoMessage(KotlinJvmBundle.message("all.modules.with.kotlin.files.are.configured"), e.presentation.text!!)
            return
        }

        when {
            configurators.size == 1 -> configurators.first().configure(project, emptyList())
            configurators.isEmpty() -> Messages.showErrorDialog(
                KotlinJvmBundle.message("there.aren.t.configurators.available"),
                e.presentation.text!!
            )
            else -> {
                val configuratorsPopup =
                    KotlinSetupEnvironmentNotificationProvider.createConfiguratorsPopup(project, configurators.toList())
                configuratorsPopup.showInBestPositionFor(e.dataContext)
            }
        }
    }
}
