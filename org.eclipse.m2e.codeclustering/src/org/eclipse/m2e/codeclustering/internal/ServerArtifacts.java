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

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;


/**
 * Maven coordinates and main class of the code-clustering language server.
 * <p>
 * The server artifact declares the clustering plugin as a {@code provided} dependency, so both artifacts are
 * resolution roots. Their runtime dependencies (LSP4J, its JSON-RPC layer and Gson) are resolved transitively.
 */
public final class ServerArtifacts {

  public static final String GROUP_ID = "org.apache.maven.plugins"; //$NON-NLS-1$

  public static final String SERVER_ARTIFACT_ID = "maven-code-clustering-lsp"; //$NON-NLS-1$

  public static final String PLUGIN_ARTIFACT_ID = "maven-code-clustering-plugin"; //$NON-NLS-1$

  /** The clang syntax tree front end: on the server class path, it atomizes C-family files through clang. */
  public static final String CLANG_ARTIFACT_ID = "maven-code-clustering-clang"; //$NON-NLS-1$

  public static final String MAIN_CLASS = "org.apache.maven.plugins.clustering.lsp.ClusteringServerMain"; //$NON-NLS-1$

  public static final String DEFAULT_VERSION = "0.1.0-SNAPSHOT"; //$NON-NLS-1$

  /** One Maven version: no range, no white space, no coordinate separator. */
  private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]*"); //$NON-NLS-1$

  private ServerArtifacts() {
  }

  /**
   * Returns the resolution roots as {@code groupId:artifactId:jar:version}, the server first.
   *
   * @param version the version preference, see {@link #version(String)}
   * @throws IllegalArgumentException if the preference is not a single Maven version
   */
  public static List<String> coordinates(String version) {
    return coordinates(version, false);
  }

  /**
   * Returns the resolution roots of {@link #coordinates(String)}, followed by the clang front end when {@code clang}
   * is true.
   *
   * @throws IllegalArgumentException if the preference is not a single Maven version
   */
  public static List<String> coordinates(String version, boolean clang) {
    String v = version(version);
    Stream<String> roots = Stream.of(coordinate(SERVER_ARTIFACT_ID, v), coordinate(PLUGIN_ARTIFACT_ID, v));
    return (clang ? Stream.concat(roots, Stream.of(coordinate(CLANG_ARTIFACT_ID, v))) : roots).toList();
  }

  /**
   * Returns the version to resolve: the preference without surrounding white space, or {@link #DEFAULT_VERSION} when
   * the preference is empty.
   *
   * @throws IllegalArgumentException if the preference is a version range or holds a character that a Maven version
   *           cannot contain
   */
  public static String version(String preference) {
    String v = preference == null ? "" : preference.strip(); //$NON-NLS-1$
    if(v.isEmpty()) {
      return DEFAULT_VERSION;
    }
    if(!VERSION.matcher(v).matches()) {
      throw new IllegalArgumentException("Not a single Maven version: '" + preference + "'"); //$NON-NLS-1$ //$NON-NLS-2$
    }
    return v;
  }

  private static String coordinate(String artifactId, String version) {
    return String.join(":", GROUP_ID, artifactId, "jar", version); //$NON-NLS-1$ //$NON-NLS-2$
  }
}
