/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Synexia Project.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.m2e.jdt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.HexFormat;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.m2e.core.project.configurator.M3EclipseBuildHandoff;

/**
 * Immutable evidence from the *original* Eclipse JDT Java project.
 *
 * <p>The original JDT model owns classpath resolution, compiler options and
 * project output location. This is a deliberately read-only, opt-in capture;
 * it neither invokes ECJ nor compiles, indexes, rewrites or caches sources.
 * The digest covers observed model declarations, NOT classpath jar bytes,
 * annotation-processor side effects, a complete source generation or ABI.
 * A consumer must never use it alone to skip a compiler or restore outputs.</p>
 */
public final class M3JdtCompilerFacts {
  public static final String SCHEMA = "M3-JDT-COMPILER-FACTS-V1";
  public static final int MAX_OPTIONS = 4096;
  public static final int MAX_CLASSPATH = 16_384;
  public static final int MAX_BYTES = 4096;

  /**
   * Detached JDT metadata bound to the original m2e build invocation.
   * These are owner claims, not a signed or successful-build certificate.
   */
  public record Snapshot(String project, String invocation, String selected,
      String outputLocation, String rawSha256, String resolvedSha256,
      String optionsSha256, int rawCount, int resolvedCount) {
    public Snapshot {
      project = checked(project, false);
      invocation = checked(invocation, false);
      selected = checked(selected, true);
      outputLocation = checked(outputLocation, false);
      rawSha256 = sha(rawSha256);
      resolvedSha256 = sha(resolvedSha256);
      optionsSha256 = sha(optionsSha256);
      if (rawCount < 0 || rawCount > MAX_CLASSPATH
          || resolvedCount < 0 || resolvedCount > MAX_CLASSPATH) {
        throw new IllegalArgumentException("JDT classpath count out of bounds");
      }
    }

    public byte[] toExchangeBytes() {
      String text = SCHEMA + "\n"
          + "provider\teclipse.jdt\n"
          + "project64\t" + b64(project) + "\n"
          + "invocation\t" + invocation + "\n"
          + "selected64\t" + b64(selected) + "\n"
          + "output64\t" + b64(outputLocation) + "\n"
          + "rawSha256\t" + rawSha256 + "\n"
          + "resolvedSha256\t" + resolvedSha256 + "\n"
          + "optionsSha256\t" + optionsSha256 + "\n"
          + "rawCount\t" + rawCount + "\n"
          + "resolvedCount\t" + resolvedCount + "\n";
      return text.getBytes(StandardCharsets.UTF_8);
    }

    public boolean authorizesBuildSkipping() {
      return false;
    }
  }

  private M3JdtCompilerFacts() { }

  /**
   * Capture original JDT raw+fully resolved classpaths and effective JDT
   * compiler options without running Java compilation.
   *
   * <p>Using getResolvedClasspath(false) is intentional: if JDT cannot resolve
   * a classpath entry, this method must fail, not emit a falsely complete
   * classpath digest. The original compiler and workspace still own success.</p>
   */
  public static Snapshot capture(IJavaProject javaProject,
      M3EclipseBuildHandoff.Snapshot handoff, IProgressMonitor monitor)
      throws JavaModelException {
    Objects.requireNonNull(javaProject, "javaProject");
    Objects.requireNonNull(handoff, "handoff");
    canceled(monitor);
    if (!javaProject.exists()) {
      throw new IllegalArgumentException("JDT Java project is unavailable");
    }
    String project = javaProject.getProject().getName();
    if (!project.equals(handoff.context().projectName())) {
      throw new IllegalArgumentException("JDT project does not belong to Eclipse handoff");
    }
    IClasspathEntry[] raw = Objects.requireNonNull(javaProject.getRawClasspath(), "raw classpath");
    canceled(monitor);
    IClasspathEntry[] resolved = Objects.requireNonNull(
        javaProject.getResolvedClasspath(false), "resolved classpath");
    canceled(monitor);
    Map<String, String> options = new TreeMap<>(Objects.requireNonNull(
        javaProject.getOptions(true), "effective JDT options"));
    if (options.size() > MAX_OPTIONS || raw.length > MAX_CLASSPATH
        || resolved.length > MAX_CLASSPATH) {
      throw new IllegalArgumentException("JDT facts exceed bounded metadata capacity");
    }
    String output = path(javaProject.getOutputLocation());
    if (output.isEmpty()) {
      throw new IllegalArgumentException("JDT project output location is unavailable");
    }
    byte[] rawHash = classpathDigest(raw, "M3-JDT-RAW-CLASSPATH-V1");
    byte[] resolvedHash = classpathDigest(resolved, "M3-JDT-RESOLVED-CLASSPATH-V1");
    MessageDigest opts = sha256();
    frame(opts, "M3-JDT-COMPILER-OPTIONS-V1");
    number(opts, options.size());
    for (var entry : options.entrySet()) {
      frame(opts, checked(entry.getKey(), false));
      frame(opts, checked(entry.getValue(), true));
    }
    canceled(monitor);
    return new Snapshot(project, handoff.invocationId(),
        handoff.context().selectedConfiguration(), output,
        HexFormat.of().formatHex(rawHash), HexFormat.of().formatHex(resolvedHash),
        HexFormat.of().formatHex(opts.digest()), raw.length, resolved.length);
  }

  private static byte[] classpathDigest(IClasspathEntry[] entries, String domain) {
    MessageDigest md = sha256();
    frame(md, domain);
    number(md, entries.length);
    for (IClasspathEntry entry : entries) {
      Objects.requireNonNull(entry, "JDT classpath entry");
      number(md, entry.getEntryKind());
      number(md, entry.getContentKind());
      number(md, entry.isExported() ? 1 : 0);
      number(md, entry.isTest() ? 1 : 0);
      frame(md, path(entry.getPath()));
      frame(md, path(entry.getOutputLocation()));
      frame(md, path(entry.getSourceAttachmentPath()));
      frame(md, path(entry.getSourceAttachmentRootPath()));
      patterns(md, entry.getInclusionPatterns());
      patterns(md, entry.getExclusionPatterns());
      IAccessRule[] rules = Objects.requireNonNull(entry.getAccessRules(), "access rules");
      number(md, rules.length);
      for (IAccessRule rule : rules) {
        number(md, rule.getKind());
        frame(md, path(rule.getPattern()));
      }
      IClasspathAttribute[] attributes = Objects.requireNonNull(
          entry.getExtraAttributes(), "JDT attributes");
      number(md, attributes.length);
      for (IClasspathAttribute attribute : attributes) {
        frame(md, checked(attribute.getName(), false));
        frame(md, checked(attribute.getValue(), true));
      }
    }
    return md.digest();
  }

  private static void patterns(MessageDigest digest, IPath[] entries) {
    Objects.requireNonNull(entries, "JDT source patterns");
    number(digest, entries.length);
    for (IPath path : entries) frame(digest, path(path));
  }

  private static String path(IPath path) {
    return path == null ? "" : checked(path.toPortableString(), true);
  }

  private static String sha(String value) {
    Objects.requireNonNull(value, "SHA-256");
    if (!value.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("noncanonical JDT SHA-256");
    }
    return value;
  }

  private static String checked(String value, boolean empty) {
    Objects.requireNonNull(value, "JDT metadata");
    if ((!empty && value.isBlank()) || value.length() > MAX_BYTES
        || value.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("invalid JDT metadata value");
    }
    for (int index = 0; index < value.length(); index++) {
      char ch = value.charAt(index);
      if (Character.isHighSurrogate(ch)) {
        if (++index >= value.length() || !Character.isLowSurrogate(value.charAt(index))) {
          throw new IllegalArgumentException("invalid JDT metadata Unicode");
        }
      } else if (Character.isLowSurrogate(ch)) {
        throw new IllegalArgumentException("invalid JDT metadata Unicode");
      }
    }
    return value;
  }

  private static String b64(String value) {
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }

  private static void number(MessageDigest digest, int value) {
    digest.update((byte) (value >>> 24));
    digest.update((byte) (value >>> 16));
    digest.update((byte) (value >>> 8));
    digest.update((byte) value);
  }

  /** Length-prefixed UTF-8 keeps classpath and option boundaries unambiguous. */
  private static void frame(MessageDigest digest, String value) {
    byte[] bytes = checked(value, true).getBytes(StandardCharsets.UTF_8);
    number(digest, bytes.length);
    digest.update(bytes);
  }

  private static void canceled(IProgressMonitor monitor) {
    if (monitor != null && monitor.isCanceled()) {
      throw new OperationCanceledException();
    }
  }
}
