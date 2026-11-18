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
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

import org.eclipse.core.resources.IncrementalProjectBuilder;

/**
 * A single advisory record assembled by the original m2e builder from the
 * source-owner IResourceDelta and the actual IBuildConfiguration of one call.
 * Supports arbitrary Eclipse builders and natures; no compiler is replaced.
 *
 * <p>The invocation ID distinguishes successive captures but is not a proof
 * of source completeness, toolchain identity, output validity or authority to
 * skip any Maven/Gradle goal. This is a source-owner export, not a build cache.</p>
 */
public final class M3EclipseBuildHandoff {
  public static final String PROPERTY = "org.eclipse.m2e.core.m3.buildHandoff";
  public static final String SCHEMA = "M3-ECLIPSE-BUILD-HANDOFF-V1";
  public static final int MAX_BYTES = 12 * 1024 * 1024;

  public record Snapshot(String invocationId, M3EclipseBuildContext.Snapshot context,
      M3WorkspaceDelta.Snapshot delta) {
    public Snapshot {
      Objects.requireNonNull(invocationId, "invocationId");
      try {
        if (!UUID.fromString(invocationId).toString().equals(invocationId)) {
          throw new IllegalArgumentException("noncanonical Eclipse invocation ID");
        }
      } catch (IllegalArgumentException invalid) {
        throw new IllegalArgumentException("invalid Eclipse invocation ID", invalid);
      }
      context = Objects.requireNonNull(context, "context");
      delta = Objects.requireNonNull(delta, "delta");
      if (!context.projectName().equals(delta.projectName())) {
        throw new IllegalArgumentException("foreign Eclipse delta for configuration");
      }
      if (delta.changes().size() > M3WorkspaceDelta.MAX_CHANGES) {
        throw new IllegalArgumentException("Eclipse delta exceeds original source-owner budget");
      }
      // Full and clean builds do not have a complete incremental delta.
      if ((delta.buildKind() == IncrementalProjectBuilder.FULL_BUILD
          || delta.buildKind() == IncrementalProjectBuilder.CLEAN_BUILD) && delta.deltaComplete()) {
        throw new IllegalArgumentException("full/clean builds cannot claim a complete delta");
      }
    }

    /** Canonical versioned export, no launcher, inferred tool or source scan. */
    public byte[] toExchangeBytes() {
      String kind = switch (delta.buildKind()) {
        case IncrementalProjectBuilder.FULL_BUILD -> "FULL";
        case IncrementalProjectBuilder.CLEAN_BUILD -> "CLEAN";
        case IncrementalProjectBuilder.INCREMENTAL_BUILD -> "INCREMENTAL";
        case IncrementalProjectBuilder.AUTO_BUILD -> "AUTO";
        default -> throw new IllegalArgumentException("unsupported Eclipse build kind");
      };
      StringBuilder out = new StringBuilder(SCHEMA).append('\n')
          .append("provider\teclipse.m2e\n")
          .append("project64\t").append(b64(context.projectName())).append('\n')
          .append("invocation\t").append(invocationId).append('\n')
          .append("selected64\t").append(b64(context.selectedConfiguration())).append('\n')
          .append("active64\t").append(b64(context.activeConfiguration())).append('\n')
          .append("kind\t").append(kind).append('\n')
          .append("complete\t").append(delta.deltaComplete()).append('\n');
      for (String config : context.configurations()) {
        out.append("configuration64\t").append(b64(config)).append('\n');
      }
      for (String builder : context.builders()) {
        out.append("builder64\t").append(b64(builder)).append('\n');
      }
      for (String nature : context.natures()) {
        out.append("nature64\t").append(b64(nature)).append('\n');
      }
      for (M3WorkspaceDelta.Change change : delta.changes()) {
        out.append("change\t").append(change.kind()).append('\t')
            .append(change.projectRelativePath()).append('\n');
      }
      byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8);
      if (bytes.length > MAX_BYTES) {
        throw new IllegalArgumentException("Eclipse handoff exceeds byte budget");
      }
      return bytes;
    }

    public boolean authorizesBuildSkipping() {
      return false;
    }
  }

  private M3EclipseBuildHandoff() { }

  /** Caller must supply both snapshots captured in the *same* MavenBuilder invocation. */
  public static Snapshot bind(M3EclipseBuildContext.Snapshot context, M3WorkspaceDelta.Snapshot delta) {
    return new Snapshot(UUID.randomUUID().toString(), context, delta);
  }

  /**
   * Explicit owner export, never automatically created on a workspace build.
   * Existing identical bytes are reused; conflicting bytes are never replaced.
   * The original Eclipse host must prevent derived-packet rebuild loops.
   */
  public static Path writeSnapshot(Snapshot snapshot, Path projectRoot, Path packetFile) throws IOException {
    Objects.requireNonNull(snapshot, "snapshot");
    Path root = Objects.requireNonNull(projectRoot, "projectRoot").toRealPath();
    Path target = Objects.requireNonNull(packetFile, "packetFile");
    Path file = (target.isAbsolute() ? target : root.resolve(target)).normalize();
    if (!file.startsWith(root) || file.equals(root)) {
      throw new IOException("Eclipse handoff packet must be project-local");
    }
    Path cursor = root;
    for (Path segment : root.relativize(file)) {
      cursor = cursor.resolve(segment);
      if (Files.isSymbolicLink(cursor)) {
        throw new IOException("Eclipse handoff path contains a symbolic link");
      }
    }
    if (!Files.isDirectory(file.getParent(), LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("Eclipse handoff parent directory must already exist");
    }
    byte[] expected = snapshot.toExchangeBytes();
    if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
      if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
          || Files.size(file) != expected.length
          || !Arrays.equals(Files.readAllBytes(file), expected)) {
        throw new IOException("conflicting Eclipse handoff packet");
      }
      return file;
    }
    Files.write(file, expected, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
        LinkOption.NOFOLLOW_LINKS);
    return file;
  }

  private static String b64(String string) {
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(string.getBytes(StandardCharsets.UTF_8));
  }
}
