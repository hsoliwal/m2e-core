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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.IPath;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.configurator.M3WorkspaceDelta;
import org.eclipse.m2e.core.project.configurator.M3WorkspaceDeltaBuildParticipant;
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
  public void ownerExchangeFormatPreservesProjectAndSortedChanges() throws Exception {
    var original = M3WorkspaceDelta.capture(project(), IncrementalProjectBuilder.INCREMENTAL_BUILD,
        delta(file("src/Z.java", IResourceDelta.REMOVED, 0, false),
            file("src/A.java", IResourceDelta.ADDED, 0, false)), null);
    String wire = new String(original.toExchangeBytes(), java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(wire.startsWith("M3-IDE-DELTA-V1\nprovider\teclipse.m2e\n"));
    assertTrue(wire.contains("kind\tINCREMENTAL\ncomplete\ttrue\n"));
    assertTrue(wire.indexOf("src/A.java") < wire.indexOf("src/Z.java"));
    assertFalse(original.authorizesMavenGoalSkipping());
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

  @Test
  public void participantInvalidatesStaleSnapshotBeforeFailedCapture() throws Exception {
    AtomicBoolean failName = new AtomicBoolean(true);
    IProject project = (IProject) Proxy.newProxyInstance(IProject.class.getClassLoader(),
        new Class<?>[] {IProject.class}, (proxy, method, args) -> {
          if(method.getName().equals("getName")) {
            if(failName.get()) {
              throw new IllegalStateException("fixture build interruption");
            }
            return "fixture";
          }
          throw new AssertionError("unexpected project method " + method.getName());
        });
    AtomicReference<Object> saved = new AtomicReference<>(new Object());
    AtomicReference<Integer> publications = new AtomicReference<>(0);
    IMavenProjectFacade facade = (IMavenProjectFacade) Proxy.newProxyInstance(
        IMavenProjectFacade.class.getClassLoader(), new Class<?>[] {IMavenProjectFacade.class},
        (proxy, method, args) -> switch(method.getName()) {
          case "getProject" -> project;
          case "setSessionProperty" -> {
            assertEquals(M3WorkspaceDeltaBuildParticipant.PROPERTY, args[0]);
            publications.set(publications.get() + 1);
            saved.set(args[1]);
            yield null;
          }
          default -> throw new AssertionError("unexpected facade method " + method.getName());
        });
    var participant = new M3WorkspaceDeltaBuildParticipant();
    // The original m2e owner installs the participant facade; do not add
    // a parallel project registry to production code merely for this test.
    Field owner = participant.getClass().getSuperclass().getSuperclass().getDeclaredField("facade");
    owner.setAccessible(true);
    owner.set(participant, facade);

    try {
      participant.build(IncrementalProjectBuilder.FULL_BUILD, null);
      fail("failed capture reused an earlier completed workspace snapshot");
    } catch(IllegalStateException expected) {
      assertEquals("fixture build interruption", expected.getMessage());
    }
    assertEquals(1, publications.get().intValue());
    assertTrue("failed capture must revoke stale advice", saved.get() == null);

    failName.set(false);
    participant.build(IncrementalProjectBuilder.FULL_BUILD, null);
    assertEquals(3, publications.get().intValue());
    assertTrue(saved.get() instanceof M3WorkspaceDelta.Snapshot);
    assertFalse(((M3WorkspaceDelta.Snapshot) saved.get()).deltaComplete());
    participant.clean(null);
    assertTrue("clean must revoke the last snapshot", saved.get() == null);
  }

  @Test
  public void directoryAndProjectMutationsCannotClaimCompleteFileDelta() throws Exception {
    var deletedFolder = M3WorkspaceDelta.capture(project(), IncrementalProjectBuilder.INCREMENTAL_BUILD,
        delta(folder("src/generated", IResourceDelta.REMOVED, 0)), null);
    assertFalse(deletedFolder.deltaComplete());
    assertTrue(deletedFolder.changes().isEmpty());
    var addedFolder = M3WorkspaceDelta.capture(project(), IncrementalProjectBuilder.AUTO_BUILD,
        delta(folder("src/new", IResourceDelta.ADDED, 0)), null);
    assertFalse(addedFolder.deltaComplete());
    assertTrue(addedFolder.changes().isEmpty());
  }

  @Test
  public void movedAndReplacedResourcesRequireReconciliation() throws Exception {
    for(int flag : new int[] {IResourceDelta.MOVED_FROM, IResourceDelta.MOVED_TO, IResourceDelta.REPLACED}) {
      var moved = M3WorkspaceDelta.capture(project(), IncrementalProjectBuilder.INCREMENTAL_BUILD,
          delta(file("src/A.java", IResourceDelta.CHANGED, IResourceDelta.CONTENT | flag, false)), null);
      assertFalse(moved.deltaComplete());
      assertEquals(1, moved.changes().size());
      assertEquals("src/A.java", moved.changes().get(0).projectRelativePath());
    }
  }

  @Test
  public void cancellationIsHonoredEvenForFullAndCleanBuilds() throws Exception {
    var canceled = (org.eclipse.core.runtime.IProgressMonitor) Proxy.newProxyInstance(
        org.eclipse.core.runtime.IProgressMonitor.class.getClassLoader(),
        new Class<?>[] {org.eclipse.core.runtime.IProgressMonitor.class}, (proxy, method, args) -> {
          if(method.getName().equals("isCanceled")) {
            return true;
          }
          throw new AssertionError("unexpected monitor method " + method.getName());
        });
    for(int kind : new int[] {IncrementalProjectBuilder.FULL_BUILD, IncrementalProjectBuilder.CLEAN_BUILD}) {
      try {
        M3WorkspaceDelta.capture(project(), kind, null, canceled);
        fail("canceled full/clean build produced a new snapshot");
      } catch(org.eclipse.core.runtime.OperationCanceledException expected) {
        // Cancellation preserves Eclipse's original build lifecycle.
      }
    }
  }

  @Test
  public void explicitPacketExportIsIdempotentAndConflictsFailClosed() throws Exception {
    Path root = Files.createTempDirectory("m3-ide-export-");
    Path parent = Files.createDirectory(root.resolve("target"));
    Path file = parent.resolve("owner.ide");
    var snapshot = new M3WorkspaceDelta.Snapshot("fixture",
        IncrementalProjectBuilder.INCREMENTAL_BUILD, true,
        List.of(new M3WorkspaceDelta.Change("src/A.java", M3WorkspaceDelta.Kind.MODIFIED)));
    var different = new M3WorkspaceDelta.Snapshot("fixture",
        IncrementalProjectBuilder.INCREMENTAL_BUILD, false, List.of());
    try {
      assertEquals(file, M3WorkspaceDelta.writeSnapshot(snapshot, root, Path.of("target/owner.ide")));
      assertArrayEquals(snapshot.toExchangeBytes(), Files.readAllBytes(file));
      assertEquals(file, M3WorkspaceDelta.writeSnapshot(snapshot, root, file));
      try {
        M3WorkspaceDelta.writeSnapshot(different, root, file);
        fail("different owner delta silently replaced a previous packet");
      } catch(IOException expected) {
        assertArrayEquals(snapshot.toExchangeBytes(), Files.readAllBytes(file));
      }
      try {
        M3WorkspaceDelta.writeSnapshot(snapshot, root, Path.of("../escape.ide"));
        fail("accepted path traversal outside the Maven project");
      } catch(IOException expected) {
        // A caller cannot export to another project or the workspace parent.
      }
    } finally {
      Files.deleteIfExists(file);
      Files.deleteIfExists(parent);
      Files.deleteIfExists(root);
    }
  }

  @Test
  public void explicitPacketExportRejectsSymlinkAncestors() throws Exception {
    Path root = Files.createTempDirectory("m3-ide-link-");
    Path actual = Files.createDirectory(root.resolve("actual"));
    Path link = root.resolve("link");
    var snapshot = new M3WorkspaceDelta.Snapshot("fixture",
        IncrementalProjectBuilder.FULL_BUILD, false, List.of());
    try {
      try {
        Files.createSymbolicLink(link, actual);
      } catch(UnsupportedOperationException | IOException | SecurityException noSymlinks) {
        return;
      }
      try {
        M3WorkspaceDelta.writeSnapshot(snapshot, root, Path.of("link/owner.ide"));
        fail("accepted symbolic link in IDE packet path");
      } catch(IOException expected) {
        assertFalse(Files.exists(actual.resolve("owner.ide")));
      }
    } finally {
      Files.deleteIfExists(link);
      Files.deleteIfExists(actual);
      Files.deleteIfExists(root);
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

  private record DeltaEntry(String path, int kind, int flags, boolean derived, int type) {}

  private static DeltaEntry file(String path, int kind, int flags, boolean derived) {
    return new DeltaEntry(path, kind, flags, derived, IResource.FILE);
  }

  private static DeltaEntry folder(String path, int kind, int flags) {
    return new DeltaEntry(path, kind, flags, false, IResource.FOLDER);
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
    IResource resource = resource(entry.path(), entry.derived(), entry.type());
    return (IResourceDelta) Proxy.newProxyInstance(IResourceDelta.class.getClassLoader(),
        new Class<?>[] {IResourceDelta.class}, (proxy, method, args) -> switch(method.getName()) {
          case "getResource" -> resource;
          case "getKind" -> entry.kind();
          case "getFlags" -> entry.flags();
          default -> throw new AssertionError("unexpected child delta method " + method.getName());
        });
  }

  private static IResource resource(String path, boolean derived, int type) {
    IPath relative = (IPath) Proxy.newProxyInstance(IPath.class.getClassLoader(),
        new Class<?>[] {IPath.class}, (proxy, method, args) -> {
          if(method.getName().equals("toPortableString")) {
            return path;
          }
          throw new AssertionError("unexpected path method " + method.getName());
        });
    return (IResource) Proxy.newProxyInstance(IResource.class.getClassLoader(),
        new Class<?>[] {IResource.class}, (proxy, method, args) -> switch(method.getName()) {
          case "getType" -> type;
          case "getProjectRelativePath" -> relative;
          case "isLinked" -> false;
          case "isDerived" -> derived;
          default -> throw new AssertionError("unexpected resource method " + method.getName());
        });
  }
}
