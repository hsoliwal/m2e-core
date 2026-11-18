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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.m2e.core.project.configurator.M3EclipseBuildContext;
import org.eclipse.m2e.core.project.configurator.M3EclipseBuildHandoff;
import org.eclipse.m2e.core.project.configurator.M3WorkspaceDelta;
import org.junit.Test;

/** Adapter-neutral Eclipse original-owner invocation evidence, with no compiler executed. */
public class M3EclipseBuildHandoffTest {
  @Test
  public void preservesJdtCdtRustAndSelectedConfigurationInOnePacket() {
    var context = context("project");
    var delta = delta("project", IncrementalProjectBuilder.INCREMENTAL_BUILD, true);
    var handoff = M3EclipseBuildHandoff.bind(context, delta);
    String wire = new String(handoff.toExchangeBytes(), StandardCharsets.UTF_8);
    assertTrue(wire.startsWith(M3EclipseBuildHandoff.SCHEMA + "\nprovider\teclipse.m2e\n"));
    assertTrue(wire.contains("selected64\tRGVidWc\n"));
    assertTrue(wire.contains("active64\tUmVsZWFzZQ\n"));
    assertTrue(wire.contains("kind\tINCREMENTAL\ncomplete\ttrue\n"));
    assertTrue(wire.indexOf("change\tADDED\tsrc/A.java") <
        wire.indexOf("change\tMODIFIED\tsrc/B.java"));
    assertTrue(wire.contains("builder64\t"));
    assertTrue(wire.contains("nature64\t"));
    assertFalse(handoff.authorizesBuildSkipping());
    assertArrayEquals(handoff.toExchangeBytes(), handoff.toExchangeBytes());
    assertTrue(handoff.invocationId().matches("[0-9a-f-]{36}"));
    assertFalse(handoff.invocationId().equals(M3EclipseBuildHandoff.bind(context, delta).invocationId()));
  }

  @Test
  public void originalEclipseBytesEqualTheCanonicalMavenDecoderFixture() throws Exception {
    var context = new M3EclipseBuildContext.Snapshot("Mixed", "Debug", "Release",
        List.of("Debug", "Release"),
        List.of("org.eclipse.jdt.core.javabuilder",
            "org.eclipse.cdt.managedbuilder.core.genmakebuilder"),
        List.of("vendor.rustdt.nature"));
    var handoff = new M3EclipseBuildHandoff.Snapshot(
        "9ab5cc33-33bd-4f33-8aa9-6c3c11223344",
        context, delta("Mixed", IncrementalProjectBuilder.INCREMENTAL_BUILD, true));
    try(InputStream in = M3EclipseBuildHandoffTest.class.getResourceAsStream(
        "/resources/m3-eclipse-build-handoff-v1.fixture")) {
      if(in == null) {
        fail("canonical Eclipse/Maven handoff fixture not included in PDE test bundle");
      }
      assertArrayEquals(in.readAllBytes(), handoff.toExchangeBytes());
    }
  }

  @Test
  public void rejectsForeignOwnerAndInvalidFullDelta() {
    try {
      M3EclipseBuildHandoff.bind(context("one"), delta("two",
          IncrementalProjectBuilder.INCREMENTAL_BUILD, true));
      fail("foreign Eclipse project was accepted");
    } catch(IllegalArgumentException expected) {
      // Config and delta must belong to one original project invocation.
    }
    try {
      M3EclipseBuildHandoff.bind(context("one"), delta("one",
          IncrementalProjectBuilder.FULL_BUILD, true));
      fail("full build falsely claimed complete delta");
    } catch(IllegalArgumentException expected) {
      // Full/clean never prove complete incremental coverage.
    }
    assertTrue(new String(M3EclipseBuildHandoff.bind(context("one"),
        delta("one", IncrementalProjectBuilder.FULL_BUILD, false)).toExchangeBytes(),
        StandardCharsets.UTF_8).contains("kind\tFULL\ncomplete\tfalse\n"));
  }

  @Test
  public void refusesUnsortedAndDuplicateSourceOwnerDeltas() {
    var wrong = new M3WorkspaceDelta.Snapshot("P",
        IncrementalProjectBuilder.INCREMENTAL_BUILD, true,
        List.of(new M3WorkspaceDelta.Change("src/B.java", M3WorkspaceDelta.Kind.MODIFIED),
            new M3WorkspaceDelta.Change("src/A.java", M3WorkspaceDelta.Kind.ADDED)));
    try {
      M3EclipseBuildHandoff.bind(context("P"), wrong);
      fail("unsorted owner changes accepted");
    } catch(IllegalArgumentException expected) {
      // A malformed source owner can never publish a complete handoff.
    }
    var duplicate = new M3WorkspaceDelta.Snapshot("P",
        IncrementalProjectBuilder.INCREMENTAL_BUILD, true,
        List.of(new M3WorkspaceDelta.Change("src/A.java", M3WorkspaceDelta.Kind.ADDED),
            new M3WorkspaceDelta.Change("src/A.java", M3WorkspaceDelta.Kind.MODIFIED)));
    try {
      M3EclipseBuildHandoff.bind(context("P"), duplicate);
      fail("duplicate owner paths accepted");
    } catch(IllegalArgumentException expected) {
      // Each project-relative source path has one unambiguous reported change.
    }
  }

  @Test
  public void explicitExportIsIdempotentAndNeverOverwritesDifferentInvocation() throws Exception {
    Path dir = Files.createTempDirectory("m3-eclipse-handoff-owner-");
    try {
      Path target = Files.createDirectories(dir.resolve("target/ide"));
      var original = M3EclipseBuildHandoff.bind(context("one"), delta("one",
          IncrementalProjectBuilder.INCREMENTAL_BUILD, true));
      Path packet = M3EclipseBuildHandoff.writeSnapshot(original, dir,
          Path.of("target/ide/build-handoff.ide"));
      assertEquals(target.resolve("build-handoff.ide"), packet);
      assertArrayEquals(original.toExchangeBytes(), Files.readAllBytes(packet));
      assertEquals(packet, M3EclipseBuildHandoff.writeSnapshot(original, dir,
          Path.of("target/ide/build-handoff.ide")));
      try {
        M3EclipseBuildHandoff.writeSnapshot(M3EclipseBuildHandoff.bind(context("one"),
            delta("one", IncrementalProjectBuilder.AUTO_BUILD, true)), dir,
            Path.of("target/ide/build-handoff.ide"));
        fail("different invocation replaced existing evidence");
      } catch(IOException expected) {
        // Original file remains untouched.
      }
      Path outside = Files.createTempFile("m3-handoff-foreign-", ".ide");
      try {
        try {
          M3EclipseBuildHandoff.writeSnapshot(original, dir, outside);
          fail("out-of-project evidence destination was accepted");
        } catch(IOException expected) {
          // No foreign writes.
        }
      } finally {
        Files.deleteIfExists(outside);
      }
    } finally {
      try(var files = Files.walk(dir)) {
        for(Path file : files.sorted(Comparator.reverseOrder()).toList()) {
          Files.deleteIfExists(file);
        }
      }
    }
  }

  private static M3EclipseBuildContext.Snapshot context(String project) {
    return new M3EclipseBuildContext.Snapshot(project, "Debug", "Release",
        List.of("Debug", "Release"),
        List.of("org.eclipse.jdt.core.javabuilder",
            "org.eclipse.cdt.managedbuilder.core.genmakebuilder"),
        List.of("org.eclipse.jdt.core.javanature",
            "org.eclipse.cdt.core.cnature", "vendor.rustdt.nature"));
  }

  private static M3WorkspaceDelta.Snapshot delta(String project, int kind, boolean complete) {
    return new M3WorkspaceDelta.Snapshot(project, kind, complete,
        List.of(new M3WorkspaceDelta.Change("src/A.java", M3WorkspaceDelta.Kind.ADDED),
            new M3WorkspaceDelta.Change("src/B.java", M3WorkspaceDelta.Kind.MODIFIED)));
  }
}
