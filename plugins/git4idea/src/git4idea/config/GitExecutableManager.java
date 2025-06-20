// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config;

import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.messages.Topic;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static git4idea.config.GitExecutableProblemHandlersKt.showUnsupportedVersionError;

/**
 * Manager for "current git executable".
 * Allows to get a path to git executable and executable version.
 */
public class GitExecutableManager {
  public static GitExecutableManager getInstance() {
    return ApplicationManager.getApplication().getService(GitExecutableManager.class);
  }

  private static final Logger LOG = Logger.getInstance(GitExecutableManager.class);

  private final @NotNull GitExecutableDetector myExecutableDetector = new GitExecutableDetector();
  private final @NotNull GitExecutableFileTester myVersionCache;

  @Topic.AppLevel
  public static final Topic<GitExecutableListener> TOPIC = new Topic<>(GitExecutableListener.class, Topic.BroadcastDirection.NONE);

  public GitExecutableManager() {
    myVersionCache = new GitExecutableFileTester();
  }

  public @NotNull String getPathToGit() {
    return getPathToGit(null);
  }

  public @NotNull String getPathToGit(@Nullable Project project) {
    String pathToGit = getPathToGit(project, null, true);
    if (pathToGit == null) pathToGit = GitExecutableDetector.getDefaultExecutable();
    return pathToGit;
  }

  private @Nullable String getPathToGit(@Nullable Project project, @Nullable Path gitDirectory, boolean detectIfNeeded) {
    String path = null;
    if (project != null && (project.isDefault() || TrustedProjects.isProjectTrusted(project))) {
      path = GitVcsSettings.getInstance(project).getPathToGit();
    }
    if (path == null) path = GitVcsApplicationSettings.getInstance().getSavedPathToGit();
    if (path == null) {
      path = myExecutableDetector.getExecutable(project, gitDirectory, detectIfNeeded);
    }
    return path;
  }

  public @NotNull GitExecutable getExecutable(@Nullable Project project) {
    return getExecutable(project, (Path)null);
  }

  // TODO remove all usages without either project or diretory
  public @NotNull GitExecutable getExecutable(@Nullable Project project, @Nullable Path gitDirectory) {
    String path = getPathToGit(project, gitDirectory, true);
    if (path == null) path = GitExecutableDetector.getDefaultExecutable();
    return getExecutable(project, path);
  }

  // not to be confused with getExecutable(Project, Path)
  public @NotNull GitExecutable getExecutable(@Nullable Project project, @NotNull String pathToGit) {
    return GitExecutableDetector.getGitExecutable(project, pathToGit);
  }

  public @Nullable String getDetectedExecutable(@Nullable Project project, boolean detectIfNeeded) {
    return myExecutableDetector.getExecutable(project, null, detectIfNeeded);
  }

  @RequiresBackgroundThread
  public void dropExecutableCache() {
    myExecutableDetector.clear();
  }

  /**
   * Get version of git executable used in project
   *
   * @return actual version or {@link GitVersion#NULL} if version could not be identified or was not identified yet
   */
  @CalledInAny
  public @NotNull GitVersion getVersion(@NotNull Project project) {
    String pathToGit = getPathToGit(project, null, false);
    if (pathToGit == null) return GitVersion.NULL;

    GitExecutable executable = getExecutable(project, pathToGit);
    return getVersion(executable);
  }

  /**
   * Get version of git executable
   *
   * @return actual version or {@link GitVersion#NULL} if version could not be identified or was not identified yet
   */
  @CalledInAny
  public @NotNull GitVersion getVersion(@NotNull GitExecutable executable) {
    GitExecutableFileTester.TestResult result = myVersionCache.getCachedResultFor(executable);
    if (result == null || result.getResult() == null) {
      return GitVersion.NULL;
    }
    else {
      return result.getResult();
    }
  }

  /**
   * Get version of git executable used in project or tell user that it cannot be obtained and cancel the operation
   * Version identification is done under progress because it can hang in rare cases
   * Usually this takes milliseconds because version is cached
   */
  @RequiresEdt
  public @NotNull GitVersion getVersionUnderModalProgressOrCancel(@NotNull Project project) throws ProcessCanceledException {
    return ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      GitExecutable executable = getExecutable(project);
      GitVersion version;
      try {
        version = identifyVersion(project, executable);
      }
      catch (GitVersionIdentificationException e) {
        throw new ProcessCanceledException();
      }
      return version;
    }, GitBundle.message("git.executable.version.progress.title"), true, project);
  }

  @CalledInAny
  public @Nullable GitVersion tryGetVersion(@NotNull Project project) {
    return runUnderProgressIfNeeded(project, GitBundle.message("git.executable.version.progress.title"), () -> {
      try {
        GitExecutable executable = getExecutable(project);
        return identifyVersion(project, executable);
      }
      catch (ProcessCanceledException | GitVersionIdentificationException e) {
        return null;
      }
    });
  }

  @CalledInAny
  public @Nullable GitVersion tryGetVersion(@Nullable Project project, @NotNull GitExecutable executable) {
    return runUnderProgressIfNeeded(project, GitBundle.message("git.executable.version.progress.title"), () -> {
      try {
        return identifyVersion(project, executable);
      }
      catch (ProcessCanceledException | GitVersionIdentificationException e) {
        return null;
      }
    });
  }

  static <T> T runUnderProgressIfNeeded(@Nullable Project project,
                                        @NotNull @NlsContexts.ProgressTitle String title,
                                        @NotNull ThrowableComputable<T, RuntimeException> task) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      return ProgressManager.getInstance().runProcessWithProgressSynchronously(task, title, true, project);
    }
    else {
      return task.compute();
    }
  }

  /**
   * Try get configured {@link #getVersion(Project)},
   * if it's {@link GitVersion#NULL} then call {@link #identifyVersion(GitExecutable)}
   */
  @RequiresBackgroundThread(generateAssertion = false)
  public @NotNull GitVersion getVersionOrIdentifyIfNeeded(@NotNull Project project) {
    var version = getVersion(project);
    if (version.isNull()) {
      version = tryGetVersion(project);
    }

    return version != null ? version : GitVersion.NULL;
  }

  /**
   * @deprecated use {@link #identifyVersion(Project, String)} instead
   */
  @Deprecated
  @RequiresBackgroundThread(generateAssertion = false)
  public @NotNull GitVersion identifyVersion(@NotNull String pathToGit) throws GitVersionIdentificationException {
    return identifyVersion(null, getExecutable(null, pathToGit));
  }

  @RequiresBackgroundThread(generateAssertion = false)
  public @NotNull GitVersion identifyVersion(@Nullable Project project, @NotNull String pathToGit)
    throws GitVersionIdentificationException {
    return identifyVersion(project, getExecutable(project, pathToGit));
  }

  /**
   * Try to identify version of git executable
   *
   * @throws GitVersionIdentificationException if there is a problem running executable or parsing version output
   */
  @RequiresBackgroundThread(generateAssertion = false)
  public @NotNull GitVersion identifyVersion(@Nullable Project project, @NotNull GitExecutable executable)
    throws GitVersionIdentificationException {
    GitExecutableFileTester.TestResult result = myVersionCache.getResultFor(project, executable);
    if (result.getResult() == null) {
      Exception e = result.getException();
      if (e instanceof NoSuchFileException &&
          executable.getExePath().equals(GitExecutableDetector.getDefaultExecutable())) {
        throw new GitNotInstalledException(GitBundle.message("executable.error.git.not.installed"), e);
      }
      throw new GitVersionIdentificationException(
        GitBundle.message("git.executable.validation.cant.identify.executable.message", executable), e);
    }
    else {
      return result.getResult();
    }
  }

  public void dropVersionCache(@NotNull GitExecutable executable) {
    myVersionCache.dropCache(executable);
  }

  public void dropVersionCache() {
    myVersionCache.dropCache();
  }

  /**
   * Check is executable used for project is valid, notify if it is not
   *
   * @return {@code true} is executable is valid, {@code false} otherwise
   */
  @RequiresBackgroundThread
  public boolean testGitExecutableVersionValid(@NotNull Project project) {
    GitExecutable executable = getExecutable(project);
    GitVersion version = identifyVersionOrDisplayError(project, executable);
    if (version == null) return false;

    GitExecutableProblemsNotifier executableProblemsNotifier = GitExecutableProblemsNotifier.getInstance(project);
    if (version.isSupported()) {
      executableProblemsNotifier.expireNotifications();
      return true;
    }
    else {
      showUnsupportedVersionError(project, version, new NotificationErrorNotifier(project));
      return false;
    }
  }

  @RequiresBackgroundThread
  private @Nullable GitVersion identifyVersionOrDisplayError(@NotNull Project project, @NotNull GitExecutable executable) {
    try {
      return identifyVersion(project, executable);
    }
    catch (GitVersionIdentificationException e) {
      GitExecutableProblemsNotifier.getInstance(project).notifyExecutionError(e);
      return null;
    }
  }
}
