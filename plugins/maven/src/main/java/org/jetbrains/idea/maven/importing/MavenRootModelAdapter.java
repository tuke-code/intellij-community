// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.MavenPathWrapper;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.jetbrains.jps.model.serialization.SerializationConstants;

import java.io.File;

public class MavenRootModelAdapter implements MavenRootModelAdapterInterface {
  private final MavenRootModelAdapterInterface myDelegate;

  public MavenRootModelAdapter(MavenRootModelAdapterInterface delegate) {myDelegate = delegate;}

  @Override
  public void init(boolean isNewlyCreatedModule) {
    myDelegate.init(isNewlyCreatedModule);
  }

  @Override
  public ModifiableRootModel getRootModel() {
    return myDelegate.getRootModel();
  }

  @Override
  public Module getModule() {
    return myDelegate.getModule();
  }

  @Override
  public <P extends JpsElement> void addSourceFolder(String path,
                                                     JpsModuleSourceRootType<P> rootType) {
    myDelegate.addSourceFolder(path, rootType);
  }

  @Override
  public void addGeneratedJavaSourceFolder(String path, JavaSourceRootType rootType) {
    myDelegate.addGeneratedJavaSourceFolder(path, rootType);
  }

  @Override
  public boolean isAlreadyExcluded(File f) {
    return myDelegate.isAlreadyExcluded(f);
  }

  @Override
  public void addExcludedFolder(String path) {
    myDelegate.addExcludedFolder(path);
  }

  @Override
  public void unregisterAll(String path, boolean under, boolean unregisterSources) {
    myDelegate.unregisterAll(path, under, unregisterSources);
  }

  @Override
  public boolean hasCollision(String sourceRootPath) {
    return myDelegate.hasCollision(sourceRootPath);
  }

  @Override
  public void useModuleOutput(String production, String test) {
    myDelegate.useModuleOutput(production, test);
  }

  @Override
  public MavenPathWrapper toPath(String path) {
    return myDelegate.toPath(path);
  }

  @Override
  public void addModuleDependency(@NotNull String moduleName, @NotNull DependencyScope scope, boolean testJar) {
    myDelegate.addModuleDependency(moduleName, scope, testJar);
  }

  @Override
  public @Nullable Module findModuleByName(String moduleName) {
    return myDelegate.findModuleByName(moduleName);
  }

  @Override
  public void addSystemDependency(MavenArtifact artifact, DependencyScope scope) {
    myDelegate.addSystemDependency(artifact, scope);
  }

  @Override
  public LibraryOrderEntry addLibraryDependency(MavenArtifact artifact,
                                                DependencyScope scope,
                                                IdeModifiableModelsProvider provider,
                                                MavenProject project) {
    return myDelegate.addLibraryDependency(artifact, scope, provider, project);
  }

  @Override
  public Library findLibrary(@NotNull MavenArtifact artifact) {
    return myDelegate.findLibrary(artifact);
  }

  @Override
  public void setLanguageLevel(LanguageLevel level) {
    myDelegate.setLanguageLevel(level);
  }

  public static boolean isChangedByUser(Library library) {
    String[] classRoots = library.getUrls(
      OrderRootType.CLASSES);
    if (classRoots.length != 1) return true;

    String classes = classRoots[0];

    if (!classes.endsWith("!/")) return true;

    int dotPos = classes.lastIndexOf("/", classes.length() - 2 /* trim ending !/ */);
    if (dotPos == -1) return true;
    String pathToJar = classes.substring(0, dotPos);

    if (hasUserPaths(OrderRootType.SOURCES, library, pathToJar)) {
      return true;
    }
    if (hasUserPaths(JavadocOrderRootType.getInstance(), library, pathToJar)) {
      return true;
    }

    return false;
  }

  private static boolean hasUserPaths(OrderRootType rootType, Library library, String pathToJar) {
    String[] sources = library.getUrls(rootType);
    for (String each : sources) {
      if (!FileUtil.startsWith(each, pathToJar)) return true;
    }
    return false;
  }

  public static boolean isMavenLibrary(@Nullable Library library) {
    return library != null && MavenArtifact.isMavenLibrary(library.getName());
  }

  public static ProjectModelExternalSource getMavenExternalSource() {
    return ExternalProjectSystemRegistry.getInstance().getSourceById(SerializationConstants.MAVEN_EXTERNAL_SOURCE_ID);
  }

  public static @Nullable OrderEntry findLibraryEntry(@NotNull Module m, @NotNull MavenArtifact artifact) {
    String name = artifact.getLibraryName();
    for (OrderEntry each : ModuleRootManager.getInstance(m).getOrderEntries()) {
      if (each instanceof LibraryOrderEntry && name.equals(((LibraryOrderEntry)each).getLibraryName())) {
        return each;
      }
    }
    return null;
  }

  public static @Nullable MavenArtifact findArtifact(@NotNull MavenProject project, @Nullable Library library) {
    if (library == null) return null;

    String name = library.getName();

    if (!MavenArtifact.isMavenLibrary(name)) return null;

    for (MavenArtifact each : project.getDependencies()) {
      if (each.getLibraryName().equals(name)) return each;
    }
    return null;
  }
}
