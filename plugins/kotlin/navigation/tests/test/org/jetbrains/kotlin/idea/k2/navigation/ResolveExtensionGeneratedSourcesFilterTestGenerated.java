// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.navigation;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode;
import org.jetbrains.kotlin.idea.base.test.TestRoot;
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.idea.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.runner.RunWith;

/**
 * This class is generated by {@link org.jetbrains.kotlin.testGenerator.generator.TestGenerator}.
 * DO NOT MODIFY MANUALLY.
 */
@SuppressWarnings("all")
@TestRoot("navigation/tests")
@TestDataPath("$CONTENT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
@TestMetadata("testData/resolveExtensionGeneratedSourcesFilter")
public class ResolveExtensionGeneratedSourcesFilterTestGenerated extends AbstractResolveExtensionGeneratedSourcesFilterTest {
    @java.lang.Override
    @org.jetbrains.annotations.NotNull
    public final KotlinPluginMode getPluginMode() {
        return KotlinPluginMode.K2;
    }

    private void runTest(String testDataFilePath) throws Exception {
        KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
    }

    @TestMetadata("memberFunction.kt")
    public void testMemberFunction() throws Exception {
        runTest("testData/resolveExtensionGeneratedSourcesFilter/memberFunction.kt");
    }

    @TestMetadata("topLevelClass.kt")
    public void testTopLevelClass() throws Exception {
        runTest("testData/resolveExtensionGeneratedSourcesFilter/topLevelClass.kt");
    }

    @TestMetadata("topLevelFunction.kt")
    public void testTopLevelFunction() throws Exception {
        runTest("testData/resolveExtensionGeneratedSourcesFilter/topLevelFunction.kt");
    }
}
