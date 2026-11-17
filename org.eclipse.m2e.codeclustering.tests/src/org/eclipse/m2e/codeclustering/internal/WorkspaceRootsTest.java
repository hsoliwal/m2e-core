/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.m2e.codeclustering.internal;

import static org.junit.Assert.assertEquals;

import java.nio.file.Path;
import java.util.List;

import org.junit.Test;


public class WorkspaceRootsTest {

  private static List<String> outermost(String... paths) {
    return WorkspaceRoots.outermost(List.of(paths), Path::of);
  }

  @Test
  public void testModulesInsideTheirParentAreDropped() {
    assertEquals(List.of("/ws/parent", "/other"),
        outermost("/ws/parent/module-a", "/ws/parent", "/other", "/ws/parent/module-a/nested"));
  }

  @Test
  public void testSiblingWithCommonPrefixIsKept() {
    assertEquals(List.of("/ws/app", "/ws/app-tests"), outermost("/ws/app", "/ws/app-tests"));
  }

  @Test
  public void testSameLocationKeepsTheFirst() {
    assertEquals(List.of("/ws/a"), WorkspaceRoots.outermost(List.of("/ws/a", "/ws/./a"), Path::of));
  }

  @Test
  public void testOrderIsPreserved() {
    assertEquals(List.of("/z", "/a", "/m"), outermost("/z", "/a", "/m", "/a/b"));
  }

  @Test
  public void testEmpty() {
    assertEquals(List.of(), outermost());
  }
}
