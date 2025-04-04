// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.actions

import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.editor.impl.editorId
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXValue
import com.intellij.xdebugger.impl.actions.areFrontendDebuggerActionsEnabled
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerEvaluateActionHandler
import com.intellij.xdebugger.impl.rpc.XDebuggerLuxApi
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase

/**
 * Frontend version of [com.intellij.xdebugger.impl.actions.EvaluateAction]
 */
private class FrontendEvaluateAction : AnAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun update(e: AnActionEvent) {
    if (!areFrontendDebuggerActionsEnabled()) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val session = e.frontendDebuggerSession
    val evaluator = session?.evaluator?.value
    if (evaluator == null) {
      e.presentation.isEnabled = false
      e.presentation.isVisible = !e.isFromContextMenu
      return
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val evaluator = e.frontendDebuggerSession?.evaluator?.value ?: return

    val focusedDataContext = XDebuggerEvaluateActionHandler.extractFocusedDataContext(e.dataContext) ?: e.dataContext
    val editor = CommonDataKeys.EDITOR.getData(focusedDataContext)
    val virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(focusedDataContext)

    val xValue = XDebuggerTreeActionBase.getSelectedNode(focusedDataContext)?.valueContainer as? FrontendXValue

    performDebuggerActionAsync(e) {
      XDebuggerLuxApi.getInstance().showLuxEvaluateDialog(
        evaluator.frameId, editor?.editorId(), virtualFile?.rpcId(), xValue?.xValueDto?.id
      )
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}