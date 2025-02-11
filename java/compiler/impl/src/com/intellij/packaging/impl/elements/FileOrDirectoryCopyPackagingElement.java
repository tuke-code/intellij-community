// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.elements;

import com.intellij.java.workspace.entities.FileOrDirectoryPackagingElementEntity;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.storage.url.VirtualFileUrl;
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager;
import com.intellij.util.xmlb.annotations.Attribute;
import kotlin.Unit;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.util.JpsPathUtil;

import java.util.Objects;

public abstract class FileOrDirectoryCopyPackagingElement<T extends FileOrDirectoryCopyPackagingElement> extends PackagingElement<T> {
  public static final @NonNls String PATH_ATTRIBUTE = "path";
  protected String myFilePath;

  public FileOrDirectoryCopyPackagingElement(PackagingElementType type) {
    super(type);
  }

  protected FileOrDirectoryCopyPackagingElement(PackagingElementType type, String filePath) {
    super(type);
    myFilePath = filePath;
  }

  public @Nullable VirtualFile findFile() {
    return LocalFileSystem.getInstance().findFileByPath(myFilePath);
  }

  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    return element instanceof FileOrDirectoryCopyPackagingElement &&
           getMyFilePath() != null &&
           getMyFilePath().equals(((FileOrDirectoryCopyPackagingElement<?>)element).getFilePath());
  }

  @Attribute(PATH_ATTRIBUTE)
  public String getFilePath() {
    return getMyFilePath();
  }

  public void setFilePath(String filePath) {
    String filePathBefore = getMyFilePath();
    this.update(
      () -> myFilePath = filePath,
      (builder, entity) -> {
        if (filePathBefore.equals(filePath)) return;

        builder.modifyEntity(FileOrDirectoryPackagingElementEntity.Builder.class, entity, ent -> {
          VirtualFileUrlManager manager = WorkspaceModel.getInstance(myProject).getVirtualFileUrlManager();
          if (filePath != null) {
            VirtualFileUrl fileUrl = manager.getOrCreateFromUrl(VfsUtilCore.pathToUrl(filePath));
            ent.setFilePath(fileUrl);
          }
          else {
            ent.setFilePath(null);
          }
          return Unit.INSTANCE;
        });
      });
  }

  @Override
  public @NotNull PackagingElementOutputKind getFilesKind(PackagingElementResolvingContext context) {
    VirtualFile file = findFile();
    if (file == null) return PackagingElementOutputKind.OTHER;

    if (file.isDirectory() && file.isInLocalFileSystem()) {
      boolean containsDirectories = false;
      boolean containsJars = false;
      for (VirtualFile child : file.getChildren()) {
        if (child.isDirectory() && child.isInLocalFileSystem()) {
          containsDirectories = true;
        }
        else {
          containsJars |= isJar(child);
        }
        if (containsDirectories && containsJars) break;
      }
      return new PackagingElementOutputKind(containsDirectories, containsJars);
    }
    return isJar(file) ? PackagingElementOutputKind.JAR_FILES : PackagingElementOutputKind.OTHER;
  }

  private static boolean isJar(VirtualFile file) {
    final String ext = file.getExtension();
    return ext != null && ext.equalsIgnoreCase("jar");
  }

  protected String getMyFilePath() {
    if (myStorage == null) {
      return myFilePath;
    } else {
      FileOrDirectoryPackagingElementEntity entity = (FileOrDirectoryPackagingElementEntity)getThisEntity();
      String path = JpsPathUtil.urlToPath(entity.getFilePath().getUrl());
      if (!Objects.equals(myFilePath, path)) {
        myFilePath = path;
      }
      return path;
    }
  }
}
