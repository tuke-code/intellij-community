/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.stubs.PyTypingAliasStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Mikhail Golubev
 */
public class PyTypingTypeAliasStubImpl implements PyTypingAliasStub {
  private final String myText;

  public PyTypingTypeAliasStubImpl(@NotNull String text) {
    myText = text;
  }

  @Override
  public @NotNull String getText() {
    return myText;
  }

  @Override
  public @NotNull Class<PyTypingAliasStubType> getTypeClass() {
    return PyTypingAliasStubType.class;
  }

  @Override
  public void serialize(@NotNull StubOutputStream stream) throws IOException {
    stream.writeName(myText);
  }

  @Override
  public @Nullable QualifiedName getCalleeName() {
    return null;
  }

  @Override
  public String toString() {
    return "PyTypingAliasStub(text='" + getText() +"')";
  }
}
