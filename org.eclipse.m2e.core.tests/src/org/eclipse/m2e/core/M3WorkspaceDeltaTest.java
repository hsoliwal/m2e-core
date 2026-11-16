/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Synexia Project.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.m2e.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Proxy;
import java.util.Arrays;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.IPath;
import org.eclipse.m2e.core.project.configurator.M3WorkspaceDelta;
import org.junit.Test;

/** Read-only source owner delta fixture; no Maven or JDT compiler is executed. */
public class M3WorkspaceDeltaTest {
  @Test
  public void unchangedMarkerAndSortedResources() throws Exception {
    var delta = delta(
        file("src/B.java", IResourceDelta.CHANGED, IResourceDelta.CONTENT, false),
        file("src/A.java", IResourceDelta.ADDED, 0, false),
        file("src/marker.java", IResourceDelta.CHANGED, IResourceDelta.MARKERS, false));
    var result = M3WorkspaceDelta.capture(project(), IncrementalProjectBuilder.INCREMENTAL_BUILD, delta, null);
    assertTrue(result.deltaComplete());
    assertEquals(2, result.changes().size());
    assertEquals("src/A.java", result.changes().get(0).projectRelativePath());
    assertEquals("src/B.java", result.changes().get(1).projectRelativePath());
    assertFalse(result.authorizesMavenGoalSkipping());
  }

  @Test
  public void removedAndDerivedFilesStayVisibleButIncomplete() throws Exception {
    var delta = delta(
        file("src/Removed.java", IResourceDelta.REMOVED, 0, false),
        file("target/classes/Generated.class", IResourceDelta.ADDED, 0, true));
    var result = M3WorkspaceDelta.capture(project(), IncrementalProjectBuilder.AUTO_BUILD, delta, null);
    assertEquals(2, result.changes().size());
    assertEquals(M3WorkspaceDelta.Kind.DELETED, result.changes().get(0).kind());
    assertFalse(result.deltaComplete());
  }

  @Test
  public void coldFullAndCleanBuildsDoNotInventDeltas() throws Exception {
    for(int kind : new int[] {IncrementalProjectBuilder.FULL_BUILD, IncrementalProjectBuilder.CLEAN_BUILD}) {
      var result = M3WorkspaceDelta.capture(project(), kind, null, null);
      assertFalse(result.deltaComplete());
      assertTrue(result.changes().isEmpty());
    }
    assertFalse(M3WorkspaceDelta.capture(project(), IncrementalProjectBuilder.INCREMENTAL_BUILD, null, null)
        .deltaComplete());
  }

  @Test
  public void cancellationNeverPublishesAnIncompleteCaptureAsSuccess() throws Exception {
    var canceled = (org.eclipse.core.runtime.IProgressMonitor) Proxy.newProxyInstance(
        org.eclipse.core.runtime.IProgressMonitor.class.getClassLoader(),
        new Class<?>[] {org.eclipse.core.runtime.IProgressMonitor.class}, (proxy, method, args) -> {
          if(method.getName().equals("isCanceled")) {
            return true;
          }
          throw new AssertionError("unexpected monitor method " + method.getName());
        });
    try {
      M3WorkspaceDelta.capture(project(), IncrementalProjectBuilder.AUTO_BUILD,
          delta(file("src/A.java", IResourceDelta.CHANGED, IResourceDelta.CONTENT, false)), canceled);
      fail("cancellation ignored");
    } catch(org.eclipse.core.runtime.OperationCanceledException expected) {
      // Original Eclipse cancellation propagates, no read-only state published.
    }
  }

  @Test
  public void duplicateAndInvalidProjectPathsRefuseCompleteCoverage() throws Exception {
    var same = delta(file("src/A.java", IResourceDelta.ADDED, 0, false),
        file("src/A.java", IResourceDelta.CHANGED, IResourceDelta.CONTENT, false));
    assertFalse(M3WorkspaceDelta.capture(project(), IncrementalProjectBuilder.INCREMENTAL_BUILD, same, null)
        .deltaComplete());
    for(String path : Arrays.asList("/escape", "../escape", "src//a", "src/./a", "src/A\\B", "src/a:b")) {
      try {
        M3WorkspaceDelta.capture(project(), IncrementalProjectBuilder.INCREMENTAL_BUILD,
            delta(file(path, IResourceDelta.ADDED, 0, false)), null);
        fail("accepted " + path);
      } catch(IllegalArgumentException expected) {
        // Expected: no unsafe path can cross the Maven boundary.
      }
    }
  }

  private static IProject project() {
    return (IProject) Proxy.newProxyInstance(IProject.class.getClassLoader(), new Class<?>[] {IProject.class},
        (proxy, method, args) -> {
          if(method.getName().equals("getName")) {
            return "fixture";
          }
          throw new AssertionError("unexpected IProject method " + method.getName());
        });
  }

  private record DeltaEntry(String path, int kind, int flags, boolean derived) {}

  private static DeltaEntry file(String path, int kind, int flags, boolean derived) {
    return new DeltaEntry(path, kind, flags, derived);
  }

  private static IResourceDelta delta(DeltaEntry... entries) {
    IResourceDelta[] children = Arrays.stream(entries).map(M3WorkspaceDeltaTest::change).toArray(IResourceDelta[]::new);
    return (IResourceDelta) Proxy.newProxyInstance(IResourceDelta.class.getClassLoader(),
        new Class<?>[] {IResourceDelta.class}, (proxy, method, args) -> {
          if(method.getName().equals("accept")) {
            IResourceDeltaVisitor visitor = (IResourceDeltaVisitor) args[0];
            for(IResourceDelta child : children) {
              visitor.visit(child);
            }
            return null;
          }
          throw new AssertionError("unexpected root delta method " + method.getName());
        });
  }

  private static IResourceDelta change(DeltaEntry entry) {
    IResource resource = resource(entry.path(), entry.derived());
    return (IResourceDelta) Proxy.newProxyInstance(IResourceDelta.class.getClassLoader(),
        new Class<?>[] {IResourceDelta.class}, (proxy, method, args) -> switch(method.getName()) {
          case "getResource" -> resource;
          case "getKind" -> entry.kind();
          case "getFlags" -> entry.flags();
          default -> throw new AssertionError("unexpected child delta method " + method.getName());
        });
  }

  private static IResource resource(String path, boolean derived) {
    IPath relative = (IPath) Proxy.newProxyInstance(IPath.class.getClassLoader(),
        new Class<?>[] {IPath.class}, (proxy, method, args) -> {
          if(method.getName().equals("toPortableString")) {
            return path;
          }
          throw new AssertionError("unexpected path method " + method.getName());
        });
    return (IResource) Proxy.newProxyInstance(IResource.class.getClassLoader(),
        new Class<?>[] {IResource.class}, (proxy, method, args) -> switch(method.getName()) {
          case "getType" -> IResource.FILE;
          case "getProjectRelativePath" -> relative;
          case "isLinked" -> false;
          case "isDerived" -> derived;
          default -> throw new AssertionError("unexpected resource method " + method.getName());
        });
  }
}
