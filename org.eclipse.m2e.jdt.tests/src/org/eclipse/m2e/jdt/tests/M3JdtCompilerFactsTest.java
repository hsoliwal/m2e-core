/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Synexia Project.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.m2e.jdt.tests;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.m2e.core.project.configurator.M3EclipseBuildContext;
import org.eclipse.m2e.core.project.configurator.M3EclipseBuildHandoff;
import org.eclipse.m2e.core.project.configurator.M3WorkspaceDelta;
import org.eclipse.m2e.jdt.M3JdtCompilerFacts;
import org.eclipse.m2e.tests.common.AbstractMavenProjectTestCase;
import org.junit.Test;

/** Real JDT-owned configuration evidence; original ECJ compilation is not run here. */
public class M3JdtCompilerFactsTest extends AbstractMavenProjectTestCase {
  @Test
  public void capturesResolvedJdtClasspathAndEffectiveCompilerOptions() throws Exception {
    IJavaProject project = importResourceProject("/projects/compilerSettings/pom.xml");
    var handoff = handoff(project.getProject().getName());
    var original = M3JdtCompilerFacts.capture(project, handoff, monitor);
    assertEquals(project.getProject().getName(), original.project());
    assertEquals(handoff.invocationId(), original.invocation());
    assertEquals("Debug", original.selected());
    assertTrue(original.rawSha256().matches("[a-f0-9]{64}"));
    assertTrue(original.resolvedSha256().matches("[a-f0-9]{64}"));
    assertTrue(original.optionsSha256().matches("[a-f0-9]{64}"));
    assertEquals(project.getRawClasspath().length, original.rawCount());
    assertEquals(project.getResolvedClasspath(false).length, original.resolvedCount());
    assertFalse(original.authorizesBuildSkipping());
    assertArrayEquals(original.toExchangeBytes(),
        M3JdtCompilerFacts.capture(project, handoff, monitor).toExchangeBytes());
    assertTrue(new String(original.toExchangeBytes(), StandardCharsets.UTF_8)
        .startsWith(M3JdtCompilerFacts.SCHEMA + "\nprovider\teclipse.jdt\n"));

    String option = JavaCore.COMPILER_SOURCE;
    String old = project.getOption(option, false);
    try {
      project.setOption(option, "21".equals(old) ? "17" : "21");
      var changed = M3JdtCompilerFacts.capture(project, handoff, monitor);
      assertNotEquals("changing the JDT compiler configuration changes its source-owner digest",
          original.optionsSha256(), changed.optionsSha256());
    } finally {
      project.setOption(option, old);
    }
  }

  @Test
  public void refusesForeignInvocationAndHonorsEclipseCancellation() throws Exception {
    IJavaProject project = importResourceProject("/projects/compilerSettings/pom.xml");
    try {
      M3JdtCompilerFacts.capture(project, handoff("WrongProject"), monitor);
      fail("foreign Eclipse invocation claimed JDT facts");
    } catch (IllegalArgumentException expected) {
      // The original Eclipse source owner must agree with the JDT project.
    }
    NullProgressMonitor canceled = new NullProgressMonitor();
    canceled.setCanceled(true);
    try {
      M3JdtCompilerFacts.capture(project, handoff(project.getProject().getName()), canceled);
      fail("canceled JDT configuration was captured");
    } catch (OperationCanceledException expected) {
      // Cancellation belongs to the original Eclipse monitor.
    }
  }

  private static M3EclipseBuildHandoff.Snapshot handoff(String name) {
    var context = new M3EclipseBuildContext.Snapshot(
        name, "Debug", "Release", List.of("Debug", "Release"),
        List.of("org.eclipse.jdt.core.javabuilder"),
        List.of("org.eclipse.jdt.core.javanature"));
    var delta = new M3WorkspaceDelta.Snapshot(
        name, IncrementalProjectBuilder.FULL_BUILD, false, List.of());
    return new M3EclipseBuildHandoff.Snapshot(
        "9ab5cc33-33bd-4f33-8aa9-6c3c11223344", context, delta);
  }
}
