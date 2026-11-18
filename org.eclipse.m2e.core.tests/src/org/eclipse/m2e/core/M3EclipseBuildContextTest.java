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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IBuildConfiguration;
import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.m2e.core.project.configurator.M3EclipseBuildContext;
import org.junit.Test;

/** Real Eclipse interface contracts without invoking any external language compiler. */
public class M3EclipseBuildContextTest {
  @Test
  public void capturesActualSelectedConfigurationNotGuessedActiveConfiguration() throws Exception {
    IProject[] ref = new IProject[1];
    IBuildConfiguration debug = config(ref, "Debug");
    IBuildConfiguration release = config(ref, "Release");
    IProjectDescription description = proxy(IProjectDescription.class, Map.of(
        "getBuildSpec", new ICommand[] {builder("org.eclipse.jdt.core.javabuilder"),
            builder("org.eclipse.cdt.managedbuilder.core.genmakebuilder"), builder("vendor.future.builder")},
        "getNatureIds", new String[] {"org.eclipse.jdt.core.javanature",
            "org.eclipse.cdt.core.cnature", "vendor.future.nature"}));
    ref[0] = proxy(IProject.class, Map.of(
        "getName", "MixedProject",
        "getBuildConfigs", new IBuildConfiguration[] {debug, release},
        "getActiveBuildConfig", release,
        "getDescription", description));

    var snapshot = M3EclipseBuildContext.capture(ref[0], debug, null);
    assertEquals("MixedProject", snapshot.projectName());
    assertEquals("Debug", snapshot.selectedConfiguration());
    assertEquals("Release", snapshot.activeConfiguration());
    assertEquals(List.of("Debug", "Release"), snapshot.configurations());
    assertEquals(List.of("org.eclipse.jdt.core.javabuilder",
        "org.eclipse.cdt.managedbuilder.core.genmakebuilder", "vendor.future.builder"), snapshot.builders());
    assertEquals(3, snapshot.natures().size());
    assertFalse(snapshot.authorizesBuildSkipping());
    String wire = new String(snapshot.toExchangeBytes(), StandardCharsets.UTF_8);
    assertTrue(wire.startsWith(M3EclipseBuildContext.SCHEMA + "\nprovider\teclipse.m2e\n"));
    assertTrue(wire.contains("selected64\tRGVidWc\n"));
    assertTrue(wire.contains("active64\tUmVsZWFzZQ\n"));
    assertTrue(wire.indexOf("configuration64\tRGVidWc") < wire.indexOf("configuration64\tUmVsZWFzZQ"));
    assertEquals(wire, new String(snapshot.toExchangeBytes(), StandardCharsets.UTF_8));
  }

  @Test
  public void defaultConfigurationRemainsDistinctFromNamedRelease() {
    var snapshot = new M3EclipseBuildContext.Snapshot("Mixed", "", "Release",
        List.of("", "Release"), List.of("vendor.builder"), List.of("vendor.nature"));
    String wire = new String(snapshot.toExchangeBytes(), StandardCharsets.UTF_8);
    assertTrue(wire.contains("selected64\t\n"));
    assertTrue(wire.contains("configuration64\t\n"));
  }

  @Test
  public void refusesForeignAndAmbiguousIdentities() throws Exception {
    rejects(() -> new M3EclipseBuildContext.Snapshot("P", "Debug", "Release",
        List.of("Debug"), List.of(), List.of()));
    rejects(() -> new M3EclipseBuildContext.Snapshot("P", "Debug", "Debug",
        List.of("Debug", "Debug"), List.of(), List.of()));
    rejects(() -> new M3EclipseBuildContext.Snapshot("P", "Bad" + (char) 0xD800, "Debug",
        List.of("Debug"), List.of(), List.of()));
    IProject[] ref = new IProject[1];
    IBuildConfiguration foreign = proxy(IBuildConfiguration.class, Map.of(
        "getName", "Debug", "getProject", proxy(IProject.class, Map.of("getName", "Other"))));
    ref[0] = proxy(IProject.class, Map.of("getName", "P"));
    rejects(() -> M3EclipseBuildContext.capture(ref[0], foreign, null));
  }

  @Test
  public void honorsEclipseCancellationBeforeReadingDescription() throws Exception {
    IProject project = proxy(IProject.class, Map.of("getName", "P"));
    IBuildConfiguration selected = proxy(IBuildConfiguration.class, Map.of("getName", "Debug"));
    IProgressMonitor canceled = proxy(IProgressMonitor.class, Map.of("isCanceled", true));
    try {
      M3EclipseBuildContext.capture(project, selected, canceled);
      fail("canceled Eclipse work must be propagated");
    } catch (OperationCanceledException expected) {
      // Original Eclipse ownership and cancellation are preserved.
    }
  }

  private static ICommand builder(String name) {
    return proxy(ICommand.class, Map.of("getBuilderName", name));
  }

  private static IBuildConfiguration config(IProject[] owner, String name) {
    return (IBuildConfiguration) Proxy.newProxyInstance(IBuildConfiguration.class.getClassLoader(),
        new Class<?>[] {IBuildConfiguration.class}, (proxy, method, args) -> switch(method.getName()) {
          case "getName" -> name;
          case "getProject" -> owner[0];
          default -> throw new AssertionError("unintended method: " + method.getName());
        });
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> api, Map<String, Object> values) {
    return (T) Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] {api},
        (instance, method, args) -> {
          if (!values.containsKey(method.getName())) {
            throw new AssertionError("unintended Eclipse API call: " + method.getName());
          }
          return values.get(method.getName());
        });
  }

  private static void rejects(Checked attempt) throws Exception {
    try {
      attempt.execute();
      fail("invalid Eclipse build configuration was accepted");
    } catch (IllegalArgumentException expected) {
      // Conservative source-owner contract.
    }
  }

  @FunctionalInterface private interface Checked { void execute() throws Exception; }
}
