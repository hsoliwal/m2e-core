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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;


public class ServerArtifactsTest {

  @Test
  public void testEmptyPreferenceGivesDefaultVersion() {
    assertEquals(ServerArtifacts.DEFAULT_VERSION, ServerArtifacts.version(null));
    assertEquals(ServerArtifacts.DEFAULT_VERSION, ServerArtifacts.version(""));
    assertEquals(ServerArtifacts.DEFAULT_VERSION, ServerArtifacts.version(" \t "));
  }

  @Test
  public void testVersionIsStripped() {
    assertEquals("0.2.0-SNAPSHOT", ServerArtifacts.version(" 0.2.0-SNAPSHOT\n"));
    assertEquals("1.0.0+build.5", ServerArtifacts.version("1.0.0+build.5"));
  }

  @Test
  public void testRangesAndSeparatorsAreRejected() {
    for(String bad : List.of("[0.1,)", "(,1.0]", "1.0 2.0", "g:a", "1.0/2", "-1.0", "1.0;x")) {
      assertThrows(bad, IllegalArgumentException.class, () -> ServerArtifacts.version(bad));
    }
  }

  @Test
  public void testCoordinatesListServerThenPlugin() {
    assertEquals(List.of("org.apache.maven.plugins:maven-code-clustering-lsp:jar:0.1.0-SNAPSHOT",
        "org.apache.maven.plugins:maven-code-clustering-plugin:jar:0.1.0-SNAPSHOT"), ServerArtifacts.coordinates(""));
    assertEquals(List.of("org.apache.maven.plugins:maven-code-clustering-lsp:jar:0.3.1",
        "org.apache.maven.plugins:maven-code-clustering-plugin:jar:0.3.1"), ServerArtifacts.coordinates("0.3.1"));
  }

  @Test
  public void testClangFrontEndJoinsTheResolutionRoots() {
    assertEquals(ServerArtifacts.coordinates("0.3.1"), ServerArtifacts.coordinates("0.3.1", false));
    assertEquals(List.of("org.apache.maven.plugins:maven-code-clustering-lsp:jar:0.3.1",
        "org.apache.maven.plugins:maven-code-clustering-plugin:jar:0.3.1",
        "org.apache.maven.plugins:maven-code-clustering-clang:jar:0.3.1"), ServerArtifacts.coordinates("0.3.1", true));
    assertThrows(IllegalArgumentException.class, () -> ServerArtifacts.coordinates("[0.1,)", true));
  }

  private static final List<File> SERVER = List.of(new File("lsp.jar"), new File("plugin.jar"));

  private final List<List<String>> asked = new ArrayList<>();

  private final List<CoreException> reported = new ArrayList<>();

  /** Resolves only roots without the clang front end, recording every request. */
  private List<File> withoutClang(List<String> roots) throws CoreException {
    asked.add(roots);
    if(roots.stream().anyMatch(root -> root.contains(ServerArtifacts.CLANG_ARTIFACT_ID))) {
      throw missing(roots);
    }
    return SERVER;
  }

  private static CoreException missing(List<String> roots) {
    return new CoreException(new Status(IStatus.ERROR, "test", "Cannot resolve " + roots));
  }

  @Test
  public void testMissingClangFrontEndFallsBackToTheServerAlone() throws CoreException {
    assertEquals(SERVER, ServerArtifacts.resolve("0.3.1", true, this::withoutClang, reported::add));
    assertEquals(List.of(ServerArtifacts.coordinates("0.3.1", true), ServerArtifacts.coordinates("0.3.1")), asked);
    assertEquals(1, reported.size());
    assertTrue(reported.get(0).getMessage().contains(ServerArtifacts.CLANG_ARTIFACT_ID));
  }

  @Test
  public void testResolvedClangFrontEndIsKept() throws CoreException {
    List<File> withClang = List.of(new File("lsp.jar"), new File("plugin.jar"), new File("clang.jar"));
    assertEquals(withClang, ServerArtifacts.resolve("0.3.1", true, roots -> {
      asked.add(roots);
      return withClang;
    }, reported::add));
    assertEquals(List.of(ServerArtifacts.coordinates("0.3.1", true)), asked);
    assertEquals(List.of(), reported);
  }

  @Test
  public void testFailureWithoutTheClangFrontEndIsNotRetried() {
    CoreException failure = missing(List.of());
    assertSame(failure, assertThrows(CoreException.class, () -> ServerArtifacts.resolve("0.3.1", false, roots -> {
      asked.add(roots);
      throw failure;
    }, reported::add)));
    assertEquals(List.of(ServerArtifacts.coordinates("0.3.1")), asked);
    assertEquals(List.of(), reported);
  }

  @Test
  public void testServerThatCannotBeResolvedFailsAfterTheFallback() {
    CoreException failure = missing(List.of());
    assertSame(failure, assertThrows(CoreException.class, () -> ServerArtifacts.resolve("0.3.1", true, roots -> {
      asked.add(roots);
      throw roots.size() == 3 ? missing(roots) : failure;
    }, reported::add)));
    assertEquals(2, asked.size());
    assertEquals(1, reported.size());
  }

  @Test
  public void testCoordinatesRejectInvalidVersion() {
    assertThrows(IllegalArgumentException.class, () -> ServerArtifacts.coordinates("[0.1,)"));
  }
}
