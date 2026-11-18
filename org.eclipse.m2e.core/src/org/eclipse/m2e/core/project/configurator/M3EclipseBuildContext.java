/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Synexia Project.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.m2e.core.project.configurator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.resources.IBuildConfiguration;
import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

/**
 * Read-only Eclipse-owned build-configuration identity for arbitrary project
 * natures and builders (JDT, CDT, Corrosion, PDE, language-server and vendor).
 *
 * <p>This captures the actual Eclipse IProject build configuration, not the
 * independent active CDT configuration or a guessed Cargo profile. It does
 * not execute a builder, resolve dependencies, claim atomic source coverage,
 * publish an input fingerprint, or authorize a Maven/Gradle goal skip.</p>
 */
public final class M3EclipseBuildContext {
  public static final String PROPERTY = "org.eclipse.m2e.core.m3.eclipseBuildContext";
  public static final String SCHEMA = "M3-ECLIPSE-BUILD-CONTEXT-V1";
  public static final int MAX_ITEMS = 2048;
  public static final int MAX_BYTES = 1024 * 1024;

  /** An exact owner configuration, with declarations in original Eclipse order. */
  public record Snapshot(String projectName, String selectedConfiguration,
      String activeConfiguration, List<String> configurations,
      List<String> builders, List<String> natures) {
    public Snapshot {
      projectName = valid(projectName, false);
      selectedConfiguration = valid(selectedConfiguration, true);
      activeConfiguration = valid(activeConfiguration, true);
      configurations = List.copyOf(Objects.requireNonNull(configurations, "configurations"));
      builders = List.copyOf(Objects.requireNonNull(builders, "builders"));
      natures = List.copyOf(Objects.requireNonNull(natures, "natures"));
      if (configurations.isEmpty() || configurations.size() > MAX_ITEMS
          || builders.size() > MAX_ITEMS || natures.size() > MAX_ITEMS) {
        throw new IllegalArgumentException("bounded Eclipse build context required");
      }
      Set<String> known = new HashSet<>();
      for (String config : configurations) {
        if (!known.add(valid(config, true))) {
          throw new IllegalArgumentException("duplicate Eclipse configuration");
        }
      }
      if (!known.contains(selectedConfiguration) || !known.contains(activeConfiguration)) {
        throw new IllegalArgumentException("selected/active Eclipse configuration not declared");
      }
      for (String builder : builders) valid(builder, false);
      for (String nature : natures) valid(nature, false);
    }

    /** Canonical LF-only owner data, independently decoded by Maven consumers. */
    public byte[] toExchangeBytes() {
      StringBuilder out = new StringBuilder(SCHEMA).append('\n')
          .append("provider\teclipse.m2e\n")
          .append("project64\t").append(b64(projectName)).append('\n')
          .append("selected64\t").append(b64(selectedConfiguration)).append('\n')
          .append("active64\t").append(b64(activeConfiguration)).append('\n');
      for (String value : configurations) out.append("configuration64\t").append(b64(value)).append('\n');
      for (String value : builders) out.append("builder64\t").append(b64(value)).append('\n');
      for (String value : natures) out.append("nature64\t").append(b64(value)).append('\n');
      byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8);
      if (bytes.length > MAX_BYTES) {
        throw new IllegalArgumentException("Eclipse context wire exceeds byte budget");
      }
      return bytes;
    }

    /** This snapshot is metadata only, not a successful compiler/cache receipt. */
    public boolean authorizesBuildSkipping() {
      return false;
    }
  }

  private M3EclipseBuildContext() { }

  /** Captures the actual IBuildConfiguration selected by Eclipse's builder. */
  public static Snapshot capture(IProject project, IBuildConfiguration selected, IProgressMonitor monitor)
      throws CoreException {
    Objects.requireNonNull(project, "project");
    Objects.requireNonNull(selected, "selected");
    checkCanceled(monitor);

    String name = project.getName();
    if (selected.getProject() == null || !name.equals(selected.getProject().getName())) {
      throw new IllegalArgumentException("foreign Eclipse build configuration");
    }
    IBuildConfiguration[] installed = project.getBuildConfigs();
    IBuildConfiguration active = project.getActiveBuildConfig();
    IProjectDescription description = project.getDescription();

    List<String> configs = new ArrayList<>(installed.length);
    for (IBuildConfiguration config : installed) {
      configs.add(Objects.requireNonNull(config, "configuration").getName());
    }
    List<String> builders = new ArrayList<>();
    for (ICommand command : description.getBuildSpec()) {
      builders.add(Objects.requireNonNull(command, "builder").getBuilderName());
    }
    List<String> natures = List.of(description.getNatureIds());
    checkCanceled(monitor);
    return new Snapshot(name, selected.getName(), active.getName(),
        configs, builders, natures);
  }

  /**
   * Explicit export only. The Eclipse source owner retains responsibility for
   * selecting a generation-specific path and excluding derived packets from
   * recursive build notifications. Existing bytes are never overwritten.
   */
  public static Path writeSnapshot(Snapshot snapshot, Path projectRoot, Path packetFile) throws IOException {
    Objects.requireNonNull(snapshot, "snapshot");
    Path root = Objects.requireNonNull(projectRoot, "projectRoot").toRealPath();
    Path requested = Objects.requireNonNull(packetFile, "packetFile");
    Path file = (requested.isAbsolute() ? requested : root.resolve(requested)).normalize();
    if (!file.startsWith(root) || file.equals(root)) {
      throw new IOException("Eclipse context destination must be project-local");
    }
    Path cursor = root;
    for (Path part : root.relativize(file)) {
      cursor = cursor.resolve(part);
      if (Files.isSymbolicLink(cursor)) {
        throw new IOException("Eclipse context destination traverses symlinks");
      }
    }
    if (!Files.isDirectory(file.getParent(), LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("Eclipse context parent must exist");
    }
    byte[] expected = snapshot.toExchangeBytes();
    if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
      if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
          || Files.size(file) != expected.length) {
        throw new IOException("Eclipse context destination already exists");
      }
      if (Arrays.equals(Files.readAllBytes(file), expected)) {
        return file;
      }
      throw new IOException("conflicting Eclipse context destination");
    }
    Files.write(file, expected, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
        LinkOption.NOFOLLOW_LINKS);
    return file;
  }

  private static void checkCanceled(IProgressMonitor monitor) {
    if (monitor != null && monitor.isCanceled()) {
      throw new OperationCanceledException();
    }
  }

  private static String b64(String value) {
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String valid(String name, boolean allowEmpty) {
    Objects.requireNonNull(name, "Eclipse build identity");
    if ((!allowEmpty && name.isBlank()) || name.length() > 1024
        || name.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("invalid Eclipse build identity");
    }
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (Character.isHighSurrogate(c)) {
        if (++i >= name.length() || !Character.isLowSurrogate(name.charAt(i))) {
          throw new IllegalArgumentException("invalid Eclipse build identity Unicode");
        }
      } else if (Character.isLowSurrogate(c)) {
        throw new IllegalArgumentException("invalid Eclipse build identity Unicode");
      }
    }
    return name;
  }
}
