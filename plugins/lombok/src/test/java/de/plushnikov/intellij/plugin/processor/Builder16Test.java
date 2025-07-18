package de.plushnikov.intellij.plugin.processor;

import com.intellij.testFramework.LightProjectDescriptor;
import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;

public class Builder16Test extends AbstractLombokParsingTestCase {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptorForNormalMode() {
    return LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR;
  }

  public void testBuilder$BuilderOnRecord() {
    doTest(true);
  }
}
