// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ether;

import org.jetbrains.jps.builders.java.JavaBuilderUtil;

import java.util.Set;

public class MemberChangeTest extends IncrementalTestCase {
  private static final Set<String> GRAPH_ONLY_TESTS =
    Set.of("addLambdaTargetMethod", "addLambdaTargetMethod2", "addLambdaTargetMethodNoRecompile", "addPackagePrivateMethodToParentClashingWithMethodFromInterface");

  public MemberChangeTest() {
    super("membersChange");
  }

  @Override
  protected boolean shouldRunTest() {
    if (JavaBuilderUtil.isDepGraphEnabled()) {
      return super.shouldRunTest();
    }
    return !GRAPH_ONLY_TESTS.contains(getTestName(true));
  }

  public void testAddAbstractMethod() {
    doTest();
  }

  public void testAddSAMInterfaceMethod() {
    doTest();
  }

  public void testDeleteSAMInterfaceMethod() {
    doTest();
  }

  public void testRenameSAMInterfaceMethod() {
    doTest();
  }

  public void testAddSAMInterfaceAbstractMethod() {
    doTest();
  }

  public void testChangeSAMInterfaceMethodToAbstract() {
    doTest();
  }

  public void testAddLambdaTargetMethod() {
    doTest();
  }

  public void testAddLambdaTargetMethod2() {
    doTest();
  }

  public void testAddLambdaTargetMethodNoRecompile() {
    doTest();
  }

  public void testAddPrivateMethodToAbstractClass() {
    doTest();
  }

  public void testAddConstructorParameter() {
    doTest();
  }

  public void testAddFieldToBaseClass() {
    doTest();
  }

  public void testAddFieldOfSameKindToBaseClass() {
    doTest();
  }

  public void testAddFieldToDerived() {
    doTest();
  }

  public void testAddFieldToInterface() {
    doTest();
  }

  public void testAddFieldToEnum() {
    doTest();
  }

  public void testAddFieldToInterface2() {
    doTest();
  }

  public void testAddFinalMethodHavingNonFinalMethodInSubclass() {
    doTest();
  }

  public void testAddHidingField() {
    doTest();
  }

  public void testAddHidingMethod() {
    doTest();
  }

  public void testAddInterfaceMethod() {
    doTest();
  }

  public void testAddInterfaceMethod2() {
    doTest();
  }

  public void testAddLessAccessibleFieldToDerived() {
    doTest();
  }

  public void testAddMethodWithIncompatibleReturnType() {
    doTest();
  }

  public void testAddMethodWithCovariantReturnType() {
    doTest();
  }

  public void testAddMoreAccessibleMethodToBase() {
    doTest();
  }

  public void testAddMoreSpecific() {
    doTest();
  }

  public void testAddMoreSpecific1() {
    doTest();
  }

  public void testAddMoreSpecific2() {
    doTest();
  }

  public void testAddNonStaticMethodHavingStaticMethodInSubclass() {
    doTest();
  }

  public void testAddStaticFieldToDerived() {
    doTest();
  }

  public void testChangeStaticMethodSignature() {
    doTest();
  }

  public void testChangeMethodGenericReturnType() {
    doTest();
  }

  public void testDeleteConstructor() {
    doTest();
  }

  public void testAddParameterToConstructor() {
    doTest();
  }

  public void testDeleteInner() {
    doTest();
  }

  public void testDeleteMethod() {
    doTest();
  }

  public void testDeleteInterfaceMethod() {
    doTest();
  }

  public void testDeleteMethodImplementation() {
    doTest();
  }

  public void testDeleteMethodImplementation2() {
    doTest();
  }

  public void testDeleteMethodImplementation3() {
    doTest();
  }

  public void testDeleteMethodImplementation4() {
    doTest();
  }

  public void testDeleteMethodImplementation5() {
    doTest();
  }

  public void testDeleteMethodImplementation6() {
    doTest();
  }

  public void testDeleteMethodImplementation7() {
    doTest();
  }

  public void testDeleteOverridingPackageLocalMethodImpl() {
    doTest().assertFailed(); // should fail because of javac bug
  }

  public void testDeleteOverridingPackageLocalMethodImpl2() {
    doTest().assertFailed();
  }

  public void testHierarchy() {
    doTest();
  }

  public void testHierarchy2() {
    doTest();
  }

  public void testRemoveBaseImplementation() {
    doTest();
  }

  public void testRemoveHidingField() {
    doTest();
  }

  public void testRemoveHidingMethod() {
    doTest();
  }

  public void testRemoveMoreAccessibleMethod() {
    doTest();
  }

  public void testRenameMethod() {
    doTest();
  }

  public void testMoveMethodToSubclass() {
    doTest().assertSuccessful();
  }
  
  public void testThrowsListDiffersInBaseAndDerived() {
    doTest();
  }

  public void testRemoveThrowsInBaseMethod() {
    doTest();
  }

  public void testAddMethod() {
    doTest();
  }

  public void testAddOverloadingMethod() {
    doTest();
  }

  public void testAddOverridingMethodAndChangeReturnType() {
    doTest();
  }

  public void testAddOverloadingConstructor() {
    doTest();
  }

  public void testAddVarargMethod() {
    doTest();
  }

  public void testReplaceMethodWithBridge() {
    doTest();
  }

  public void testPushFieldDown() {
    doTest();
  }

  public void testAddPackagePrivateMethodToParentClashingWithMethodFromInterface() {
    doTest().assertFailed();
  }
}
