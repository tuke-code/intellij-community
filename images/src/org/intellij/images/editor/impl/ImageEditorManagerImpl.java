/*
 * Copyright 2004-2005 Alexey Efimov
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
package org.intellij.images.editor.impl;

import org.intellij.images.options.*;
import org.intellij.images.ui.ImageComponent;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;

/**
 * Image viewer manager implementation.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
@Internal
public final class ImageEditorManagerImpl {
  private ImageEditorManagerImpl() {
  }

  public static @NotNull ImageEditorUI createImageEditorUI(BufferedImage image) {
    return createImageEditorUI(image, null);
  }

  public static @NotNull ImageEditorUI createImageEditorUI(BufferedImage image, @Nullable String format) {
    ImageEditorUI ui = new ImageEditorUI(null);
    Options options = OptionsManager.getInstance().getOptions();
    EditorOptions editorOptions = options.getEditorOptions();
    GridOptions gridOptions = editorOptions.getGridOptions();
    TransparencyChessboardOptions transparencyChessboardOptions = editorOptions.getTransparencyChessboardOptions();
    ui.getImageComponent().setGridVisible(gridOptions.isShowDefault());
    ui.getImageComponent().setTransparencyChessboardVisible(transparencyChessboardOptions.isShowDefault());

    ui.setImageProvider((scale, ancestor) -> image, format);
    return ui;
  }

  public static ImageComponent getImageComponent(ImageEditorUI comp) {
    return comp.getImageComponent();
  }
}
