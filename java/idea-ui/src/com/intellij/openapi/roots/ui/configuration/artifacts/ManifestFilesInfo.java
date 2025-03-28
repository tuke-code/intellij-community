// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.elements.ManifestFileUtil;
import com.intellij.packaging.ui.ManifestFileConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ManifestFilesInfo {
  private static final Logger LOG = Logger.getInstance(ManifestFilesInfo.class);
  private final Map<VirtualFile, ManifestFileConfiguration> myManifestFiles = new HashMap<>();
  private final Map<VirtualFile, ManifestFileConfiguration> myOriginalManifestFiles = new HashMap<>();

  public @Nullable ManifestFileConfiguration getManifestFile(CompositePackagingElement<?> element, ArtifactType artifactType,
                                                             final PackagingElementResolvingContext context) {
    final VirtualFile manifestFile = ManifestFileUtil.findManifestFile(element, context, artifactType);
    if (manifestFile == null) {
      return null;
    }

    ManifestFileConfiguration configuration = myManifestFiles.get(manifestFile);
    if (configuration == null) {
      configuration = ManifestFileUtil.createManifestFileConfiguration(manifestFile);
      myOriginalManifestFiles.put(manifestFile, new ManifestFileConfiguration(configuration));
      myManifestFiles.put(manifestFile, configuration);
    }
    return configuration;
  }

  public void saveManifestFiles() {
    for (Map.Entry<VirtualFile, ManifestFileConfiguration> entry : myManifestFiles.entrySet()) {
      final ManifestFileConfiguration configuration = entry.getValue();
      final String path = configuration.getManifestFilePath();
      if (path == null) continue;

      final ManifestFileConfiguration original = myOriginalManifestFiles.get(entry.getKey());
      if (original != null && original.equals(configuration)) {
        continue;
      }

      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
      if (file == null) {
        final File ioFile = new File(FileUtil.toSystemDependentName(path));
        FileUtil.createIfDoesntExist(ioFile);
        file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
        if (file == null) {
          //todo improve error reporting
          LOG.error("cannot create file: " + ioFile);
        }
      }

      ManifestFileUtil.updateManifest(file, configuration.getMainClass(), configuration.getClasspath(), true);
    }
  }

  public boolean isManifestFilesModified() {
    return !myOriginalManifestFiles.equals(myManifestFiles);
  }

  public void clear() {
    myManifestFiles.clear();
    myOriginalManifestFiles.clear();
  }
}
