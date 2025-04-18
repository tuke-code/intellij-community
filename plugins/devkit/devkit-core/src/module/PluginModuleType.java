// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.module;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.openapi.module.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.IntelliJProjectUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.descriptors.ConfigFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.build.PluginBuildConfiguration;
import org.jetbrains.idea.devkit.build.PluginBuildUtil;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PluginModuleType extends ModuleType<JavaModuleBuilder> {

  public static final @NonNls String ID = "PLUGIN_MODULE";

  public PluginModuleType() {
    super(ID);
  }

  public static PluginModuleType getInstance() {
    return (PluginModuleType)ModuleTypeManager.getInstance().findByID(ID);
  }

  public static boolean isOfType(@NotNull Module module) {
    return get(module) instanceof PluginModuleType;
  }

  @Override
  public @NotNull JavaModuleBuilder createModuleBuilder() {
    return new JavaModuleBuilder();
  }

  @Override
  public @NotNull String getName() {
    return DevKitBundle.message("module.title");
  }

  @Override
  public @NotNull String getDescription() {
    return DevKitBundle.message("module.description");
  }

  @Override
  public @NotNull Icon getNodeIcon(boolean isOpened) {
    return AllIcons.Nodes.Plugin;
  }

  public static @Nullable XmlFile getPluginXml(Module module) {
    if (module == null) return null;
    if (!isOfType(module)) {
      for (final ContentEntry entry : ModuleRootManager.getInstance(module).getContentEntries()) {
        for (final SourceFolder folder : entry.getSourceFolders(JavaModuleSourceRootTypes.PRODUCTION)) {
          final VirtualFile file = folder.getFile();
          if (file == null) continue;

          final String packagePrefix = folder.getPackagePrefix();
          final String prefixPath = packagePrefix.isEmpty() ? "" :
                                    packagePrefix.replace('.', '/') + '/';

          final String relativePath = prefixPath + PluginDescriptorConstants.PLUGIN_XML_PATH;
          final VirtualFile pluginXmlVF = file.findFileByRelativePath(relativePath);
          if (pluginXmlVF != null) {
            final PsiFile psiFile = PsiManager.getInstance(module.getProject()).findFile(pluginXmlVF);
            if (psiFile instanceof XmlFile) {
              return (XmlFile)psiFile;
            }
          }
        }
      }

      return null;
    }

    final PluginBuildConfiguration buildConfiguration = PluginBuildConfiguration.getInstance(module);
    if (buildConfiguration == null) return null;
    final ConfigFile configFile = buildConfiguration.getPluginXmlConfigFile();
    return configFile != null ? configFile.getXmlFile() : null;
  }

  public static boolean isPluginModuleOrDependency(@Nullable Module module) {
    if (module == null) return false;
    if (isOfType(module)) return true;
    return !getCandidateModules(module).isEmpty();
  }

  public static List<Module> getCandidateModules(Module module) {
    if (IntelliJProjectUtil.isIntelliJPlatformProject(module.getProject())) {
      Set<Module> dependents = new HashSet<>();
      ModuleUtilCore.collectModulesDependsOn(module, dependents);
      return new ArrayList<>(dependents);
    }

    final Module[] modules = ModuleManager.getInstance(module.getProject()).getModules();
    final List<Module> candidates = new ArrayList<>(modules.length);
    final Set<Module> deps = new HashSet<>(modules.length);
    for (Module m : modules) {
      if (get(m) == getInstance()) {
        deps.clear();
        PluginBuildUtil.getDependencies(m, deps);

        if (deps.contains(module) && getPluginXml(m) != null) {
          candidates.add(m);
        }
      }
    }
    return candidates;
  }

  @Override
  public boolean isValidSdk(final @NotNull Module module, final Sdk projectSdk) {
    return JavaModuleType.isValidJavaSdk(module);
  }
}
