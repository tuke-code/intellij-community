// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.uv

import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.text.nullize
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.uv.impl.setUvExecutable
import com.jetbrains.python.sdk.uv.setupUvSdkUnderProgress
import com.jetbrains.python.statistics.InterpreterType
import java.nio.file.Path
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.asPythonResult
import com.jetbrains.python.sdk.add.v2.CustomNewEnvironmentCreator
import com.jetbrains.python.sdk.add.v2.PythonMutableTargetAddInterpreterModel

internal class EnvironmentCreatorUv(model: PythonMutableTargetAddInterpreterModel) : CustomNewEnvironmentCreator("uv", model) {
  override val interpreterType: InterpreterType = InterpreterType.UV
  override val executable: ObservableMutableProperty<String> = model.state.uvExecutable
  override val installationVersion: String? = null

  override fun onShown() {
    // FIXME: validate base interpreters against pyprojecttoml version. See poetry
    basePythonComboBox.setItems(model.baseInterpreters)
  }

  override fun savePathToExecutableToProperties(path: Path?) {
    val savingPath = path ?: executable.get().nullize()?.let { Path.of(it) } ?: return
    setUvExecutable(savingPath)
  }

  override suspend fun setupEnvSdk(project: Project?, module: Module?, baseSdks: List<Sdk>, projectPath: String, homePath: String?, installPackages: Boolean): Result<Sdk, PyError> {
    if (module == null) {
      // FIXME: should not happen, proper error
      return kotlin.Result.failure<Sdk>(Exception("module is null")).asPythonResult()
    }

    val python = homePath?.let { Path.of(it) }
    return setupUvSdkUnderProgress(ModuleOrProject.ModuleAndProject(module), baseSdks, python).asPythonResult()
  }

  override suspend fun detectExecutable() {
    model.detectUvExecutable()
  }
}