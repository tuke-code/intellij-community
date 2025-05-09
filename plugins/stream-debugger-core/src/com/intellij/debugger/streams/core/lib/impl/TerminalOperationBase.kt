// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.lib.impl

import com.intellij.debugger.streams.core.lib.TerminalOperation
import com.intellij.debugger.streams.core.resolve.ValuesOrderResolver
import com.intellij.debugger.streams.core.trace.CallTraceInterpreter
import com.intellij.debugger.streams.core.trace.TerminatorCallHandler
import com.intellij.debugger.streams.core.trace.dsl.Dsl
import com.intellij.debugger.streams.core.wrapper.TerminatorStreamCall

/**
 * @author Vitaliy.Bibaev
 */
abstract class TerminalOperationBase(override val name: String,
                                     private val handlerFactory: (TerminatorStreamCall, String, dsl: Dsl) -> TerminatorCallHandler,
                                     override val traceInterpreter: CallTraceInterpreter,
                                     override val valuesOrderResolver: ValuesOrderResolver) : TerminalOperation {
  override fun getTraceHandler(call: TerminatorStreamCall, resultExpression: String, dsl: Dsl): TerminatorCallHandler =
    handlerFactory.invoke(call, resultExpression, dsl)
}