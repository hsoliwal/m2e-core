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
import static org.junit.Assert.assertThrows;

import java.util.List;

import org.junit.Test;


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
  public void testCoordinatesRejectInvalidVersion() {
    assertThrows(IllegalArgumentException.class, () -> ServerArtifacts.coordinates("[0.1,)"));
  }
}
