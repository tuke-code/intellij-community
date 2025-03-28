// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public abstract class SimpleCoverageAnnotator extends BaseCoverageAnnotator {

  private final Map<String, FileCoverageInfo> myFileCoverageInfos = new HashMap<>();
  private final Map<String, DirCoverageInfo> myTestDirCoverageInfos = new HashMap<>();
  private final Map<String, DirCoverageInfo> myDirCoverageInfos = new HashMap<>();
  private final Set<String> myTestDirectories = new HashSet<>();

  public SimpleCoverageAnnotator(Project project) {
    super(project);
  }

  @Override
  public void onSuiteChosen(CoverageSuitesBundle newSuite) {
    super.onSuiteChosen(newSuite);

    myFileCoverageInfos.clear();
    myTestDirCoverageInfos.clear();
    myDirCoverageInfos.clear();
    myTestDirectories.clear();
  }

  protected @Nullable DirCoverageInfo getDirCoverageInfo(@NotNull VirtualFile dir,
                                                         @NotNull CoverageSuitesBundle currentSuite) {
    final String path = normalizeFilePath(dir.getPath());

    final boolean isInTestContent = myTestDirectories.contains(path);
    if (!currentSuite.isTrackTestFolders() && isInTestContent) {
      return null;
    }

    return isInTestContent ? myTestDirCoverageInfos.get(path) : myDirCoverageInfos.get(path);
  }

  @Override
  public @Nullable @Nls String getDirCoverageInformationString(@NotNull Project project,
                                                               @NotNull VirtualFile dir,
                                                               @NotNull CoverageSuitesBundle currentSuite,
                                                               @NotNull CoverageDataManager manager) {
    DirCoverageInfo coverageInfo = getDirCoverageInfo(dir, currentSuite);
    if (coverageInfo == null) {
      return null;
    }

    if (manager.isSubCoverageActive()) {
      return coverageInfo.coveredLineCount > 0 ? CoverageBundle.message("coverage.view.text.covered") : null;
    }

    final String filesCoverageInfo = getFilesCoverageInformationString(coverageInfo);
    if (filesCoverageInfo != null) {
      final @Nls StringBuilder builder = new StringBuilder();
      builder.append(filesCoverageInfo);
      final String linesCoverageInfo = getLinesCoverageInformationString(coverageInfo);
      if (linesCoverageInfo != null) {
        builder.append(", ").append(linesCoverageInfo);
      }
      return builder.toString();
    }
    return null;
  }

  /**
   * @deprecated SimpleCoverageAnnotator doesn't require normalized file paths any more
   * so now coverage report should work w/o usage of this method
  */
  @Deprecated(forRemoval = true)
  public static String getFilePath(final String filePath) {
    return normalizeFilePath(filePath);
  }

  protected static @NotNull
  String normalizeFilePath(@NotNull String filePath) {
    if (SystemInfo.isWindows) {
      filePath = StringUtil.toLowerCase(filePath);
    }
    return FileUtil.toSystemIndependentName(filePath);
  }

  @Override
  public @Nullable @Nls String getFileCoverageInformationString(@NotNull Project project,
                                                                @NotNull VirtualFile file,
                                                                @NotNull CoverageSuitesBundle currentSuite,
                                                                @NotNull CoverageDataManager manager) {
    final String path = normalizeFilePath(file.getPath());

    final FileCoverageInfo coverageInfo = myFileCoverageInfos.get(path);
    if (coverageInfo == null) {
      return null;
    }

    if (manager.isSubCoverageActive()) {
      return coverageInfo.coveredLineCount > 0 ? CoverageBundle.message("coverage.view.text.covered") : null;
    }

    return getLinesCoverageInformationString(coverageInfo);
  }

  protected @Nullable FileCoverageInfo collectBaseFileCoverage(@NotNull VirtualFile file,
                                                               final @NotNull SimpleCoverageAnnotator.CoverageAnnotatorRunner annotator,
                                                               final @NotNull ProjectData projectData,
                                                               final @NotNull Map<String, String> normalizedFiles2Files) {

    file = file.getCanonicalFile() != null ? file.getCanonicalFile() : file;
    assert file != null;

    final String filePath = normalizeFilePath(file.getPath());

    // process file
    final FileCoverageInfo info;

    final ClassData classData = getClassData(filePath, projectData, normalizedFiles2Files);
    if (classData != null) {
      // fill info from coverage data
      info = fileInfoForCoveredFile(classData);
    }
    else {
      // file wasn't mentioned in coverage information
      info = fillInfoForUncoveredFile(VfsUtilCore.virtualToIoFile(file));
    }

    if (info != null) {
      annotator.annotateFile(filePath, info);
    }
    return info;
  }

  protected static @Nullable
  ClassData getClassData(
    final @NotNull String filePath,
    final @NotNull ProjectData data,
    final @NotNull Map<String, String> normalizedFiles2Files) {
    final String originalFileName = normalizedFiles2Files.get(filePath);
    if (originalFileName == null) {
      return null;
    }
    return data.getClassData(originalFileName);
  }

  protected @Nullable DirCoverageInfo collectFolderCoverage(final @NotNull VirtualFile dir,
                                                            final @NotNull CoverageDataManager dataManager,
                                                            final CoverageAnnotatorRunner annotator,
                                                            final ProjectData projectInfo, boolean trackTestFolders,
                                                            final @NotNull ProjectFileIndex index,
                                                            final @NotNull CoverageEngine coverageEngine,
                                                            Set<? super VirtualFile> visitedDirs,
                                                            final @NotNull Map<String, String> normalizedFiles2Files) {
    if (ReadAction.compute(() -> !index.isInContent(dir) && !index.isInLibrary(dir))) {
      return null;
    }

    if (visitedDirs.contains(dir)) {
      return null;
    }

    if (!shouldCollectCoverageInsideLibraryDirs()) {
      if (ReadAction.compute(() -> index.isInLibrary(dir))) {
        return null;
      }
    }
    visitedDirs.add(dir);

    final String dirPath = normalizeFilePath(dir.getPath());

    final boolean isInTestSrcContent = ReadAction.compute(() -> TestSourcesFilter.isTestSources(dir, getProject()));
    if (isInTestSrcContent) {
      myTestDirectories.add(dirPath);
    }

    // Don't count coverage for tests folders if track test folders is switched off
    if (!trackTestFolders && isInTestSrcContent) {
      return null;
    }

    final VirtualFile[] children = dataManager.doInReadActionIfProjectOpen(dir::getChildren);
    if (children == null) {
      return null;
    }

    final DirCoverageInfo dirCoverageInfo = new DirCoverageInfo();

    for (VirtualFile fileOrDir : children) {
      if (fileOrDir.isDirectory()) {
        final DirCoverageInfo childCoverageInfo =
          collectFolderCoverage(fileOrDir, dataManager, annotator, projectInfo, trackTestFolders, index,
                                coverageEngine, visitedDirs, normalizedFiles2Files);

        if (childCoverageInfo != null) {
          dirCoverageInfo.totalFilesCount += childCoverageInfo.totalFilesCount;
          dirCoverageInfo.coveredFilesCount += childCoverageInfo.coveredFilesCount;
          dirCoverageInfo.totalLineCount += childCoverageInfo.totalLineCount;
          dirCoverageInfo.coveredLineCount += childCoverageInfo.coveredLineCount;
        }
      }
      else if (coverageEngine.coverageProjectViewStatisticsApplicableTo(fileOrDir)) {
        // let's count statistics only for ruby-based files

        final FileCoverageInfo fileInfo =
          collectBaseFileCoverage(fileOrDir, annotator, projectInfo, normalizedFiles2Files);

        if (fileInfo != null) {
          dirCoverageInfo.totalLineCount += fileInfo.totalLineCount;
          dirCoverageInfo.totalFilesCount++;

          if (fileInfo.coveredLineCount > 0) {
            dirCoverageInfo.coveredFilesCount++;
            dirCoverageInfo.coveredLineCount += fileInfo.coveredLineCount;
          }
        }
      }
    }


    //TODO - toplevelFilesCoverage - is unused variable!

    // no sense to include directories without ruby files
    if (dirCoverageInfo.totalFilesCount == 0) {
      return null;
    }

    if (isInTestSrcContent) {
      annotator.annotateTestDirectory(dirPath, dirCoverageInfo);
    }
    else {
      annotator.annotateSourceDirectory(dirPath, dirCoverageInfo);
    }

    return dirCoverageInfo;
  }

  protected boolean shouldCollectCoverageInsideLibraryDirs() {
    // By default returns "true" for backward compatibility
    return true;
  }

  protected void annotate(final @NotNull VirtualFile contentRoot,
                          final @NotNull CoverageSuitesBundle suite,
                          final @NotNull CoverageDataManager dataManager, final @NotNull ProjectData data,
                          final Project project,
                          final CoverageAnnotatorRunner annotator) {
    if (!contentRoot.isValid()) {
      return;
    }

    // TODO: check name filter!!!!!

    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();

    final Map<String, String> normalizedFiles2Files = getNormalizedFiles2FilesMapping(data);
    collectFolderCoverage(contentRoot, dataManager, annotator, data,
                          suite.isTrackTestFolders(),
                          index,
                          suite.getCoverageEngine(),
                          new HashSet<>(),
                          Collections.unmodifiableMap(normalizedFiles2Files));
  }

  @ApiStatus.Internal
  protected Map<String, String> getNormalizedFiles2FilesMapping(ProjectData data) {
    final Map<String, String> normalizedFiles2Files = new HashMap<>();
    final Set<String> files = data.getClasses().keySet();
    for (final String file : files) {
      normalizedFiles2Files.put(normalizeFilePath(file), file);
    }
    return normalizedFiles2Files;
  }

  @Override
  protected @Nullable Runnable createRenewRequest(final @NotNull CoverageSuitesBundle suite, final @NotNull CoverageDataManager dataManager) {
    final ProjectData data = suite.getCoverageData();
    if (data == null) {
      return null;
    }

    return () -> {
      final Project project = getProject();

      final VirtualFile[] modulesContentRoots = getRoots(project, dataManager, suite);

      if (modulesContentRoots == null) {
        return;
      }

      // gather coverage from all content roots
      for (VirtualFile root : modulesContentRoots) {
        annotate(root, suite, dataManager, data, project, new CoverageAnnotatorRunner() {
          @Override
          public void annotateSourceDirectory(final String dirPath, final DirCoverageInfo info) {
            myDirCoverageInfos.put(dirPath, info);

            try {
              myDirCoverageInfos.put((new File(dirPath)).getCanonicalPath(), info);
            }
            catch (IOException e) {
              //pass
            }
          }

          @Override
          public void annotateTestDirectory(final String dirPath, final DirCoverageInfo info) {
            myTestDirCoverageInfos.put(dirPath, info);

            try {
              myTestDirCoverageInfos.put((new File(dirPath)).getCanonicalPath(), info);
            }
            catch (IOException e) {
              //pass
            }
          }

          @Override
          public void annotateFile(final @NotNull String filePath, final @NotNull FileCoverageInfo info) {
            myFileCoverageInfos.put(filePath, info);

            try {
              myFileCoverageInfos.put((new File(filePath)).getCanonicalPath(), info);
            }
            catch (IOException e) {
              //pass
            }
          }
        });
      }

      //final VirtualFile[] roots = ProjectRootManagerEx.getInstanceEx(project).getContentRootsFromAllModules();
      //index.iterateContentUnderDirectory(roots[0], new ContentIterator() {
      //  public boolean processFile(final VirtualFile fileOrDir) {
      //    // TODO support for libraries and sdk
      //    if (index.isInContent(fileOrDir)) {
      //      final String normalizedPath = RubyCoverageEngine.rcovalizePath(fileOrDir.getPath(), (RubyCoverageSuite)suite);
      //
      //      // TODO - check filters
      //
      //      if (fileOrDir.isDirectory()) {
      //        //// process dir
      //        //if (index.isInTestSourceContent(fileOrDir)) {
      //        //  //myTestDirCoverageInfos.put(RubyCoverageEngine.rcovalizePath(fileOrDir.getPath(), (RubyCoverageSuite)suite), )
      //        //} else {
      //        //  myDirCoverageInfos.put(normalizedPath, new FileCoverageInfo());
      //        //}
      //      } else {
      //        // process file
      //        final ClassData classData = data.getOrCreateClassData(normalizedPath);
      //        if (classData != null) {
      //          final int count = classData.getLines().length;
      //          if (count != 0) {
      //            final FileCoverageInfo info = new FileCoverageInfo();
      //            info.totalLineCount = count;
      //            // let's count covered lines
      //            for (int i = 1; i <= count; i++) {
      //              final LineData lineData = classData.getLineData(i);
      //              if (lineData.getStatus() != LineCoverage.NONE){
      //                info.coveredLineCount++;
      //              }
      //            }
      //            myFileCoverageInfos.put(normalizedPath, info);
      //          }
      //        }
      //      }
      //    }
      //    return true;
      //  }
      //});

      dataManager.triggerPresentationUpdate();
    };
  }

  @ApiStatus.Internal
  protected VirtualFile[] getRoots(Project project,
                                   @NotNull CoverageDataManager dataManager,
                                   CoverageSuitesBundle suite) {
    final ProjectRootManager rootManager = ProjectRootManager.getInstance(project);

    // find all modules content roots
    return dataManager.doInReadActionIfProjectOpen(() -> rootManager.getContentRoots());
  }

  protected @Nullable @Nls String getLinesCoverageInformationString(final @NotNull FileCoverageInfo info) {
    return CoverageBundle.message("coverage.view.text.lines.covered", calcCoveragePercentage(info));
  }

  protected static int calcCoveragePercentage(@NotNull FileCoverageInfo info) {
    return calcPercent(info.coveredLineCount, info.totalLineCount);
  }

  @ApiStatus.Internal
  protected static int calcPercent(final int covered, final int total) {
    return total != 0 ? (int)((double)covered / total * 100) : 100;
  }

  protected @Nullable @Nls String getFilesCoverageInformationString(final @NotNull DirCoverageInfo info) {
    return CoverageBundle.message("coverage.view.text.files.covered", calcPercent(info.coveredFilesCount, info.totalFilesCount));
  }

  protected @Nullable FileCoverageInfo fileInfoForCoveredFile(final @NotNull ClassData classData) {
    final Object[] lines = classData.getLines();

    // class data lines = [0, 1, ... count] but first element with index = #0 is fake and isn't
    // used thus count = length = 1
    final int count = lines.length - 1;

    if (count == 0) {
      return null;
    }

    final FileCoverageInfo info = new FileCoverageInfo();

    info.coveredLineCount = 0;
    info.totalLineCount = 0;
    // let's count covered lines
    for (int i = 1; i <= count; i++) {
      final LineData lineData = classData.getLineData(i);

      processLineData(info, lineData);
    }
    return info;
  }

  protected void processLineData(@NotNull FileCoverageInfo info, @Nullable LineData lineData) {
    if (lineData == null) {
      // Ignore not src code
      return;
    }
    final int status = lineData.getStatus();
    // covered - if src code & covered (or inferred covered)

    if (status != LineCoverage.NONE) {
      info.coveredLineCount++;
    }
    info.totalLineCount++;
  }

  protected @Nullable FileCoverageInfo fillInfoForUncoveredFile(@NotNull File file) {
    return null;
  }

  protected interface CoverageAnnotatorRunner {
    void annotateSourceDirectory(final String dirPath, final DirCoverageInfo info);

    void annotateTestDirectory(final String dirPath, final DirCoverageInfo info);

    void annotateFile(final @NotNull String filePath, final @NotNull FileCoverageInfo info);
  }
}
