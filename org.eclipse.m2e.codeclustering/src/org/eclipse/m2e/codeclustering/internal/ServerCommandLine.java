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

import java.io.File;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


/**
 * The command that starts the code-clustering language server on standard input and output.
 */
public final class ServerCommandLine {

  static final String PROFILES_PROPERTY = "-Dclustering.lsp.profiles="; //$NON-NLS-1$

  static final String SERVERS_PROPERTY = "-Dclustering.lsp.servers="; //$NON-NLS-1$

  static final String CLANG_EXECUTABLE_PROPERTY = "-Dclustering.clang.executable="; //$NON-NLS-1$

  private ServerCommandLine() {
  }

  /**
   * Returns the Java executable: the preference when it is set (a JDK directory gets {@code bin/java}), else the
   * {@code bin/java} of {@code javaHome}, the Java that runs Eclipse.
   */
  public static String javaExecutable(String preference, String javaHome, boolean windows) {
    String executable = windows ? "java.exe" : "java"; //$NON-NLS-1$ //$NON-NLS-2$
    if(preference == null || preference.isBlank()) {
      return Path.of(javaHome, "bin", executable).toString(); //$NON-NLS-1$
    }
    Path given = Path.of(preference.strip());
    return Files.isDirectory(given) ? given.resolve("bin").resolve(executable).toString() : given.toString(); //$NON-NLS-1$
  }

  /**
   * Returns {@code java [vm arguments] [-Dclustering.lsp.profiles=..] [-Dclustering.lsp.servers=..]
   * [-Dclustering.clang.executable=..] -cp <class path> ClusteringServerMain}.
   *
   * @throws IllegalArgumentException if the class path is empty
   */
  public static List<String> build(ServerOptions options, String java, List<File> classpath) {
    if(classpath.isEmpty()) {
      throw new IllegalArgumentException("The code-clustering server class path is empty"); //$NON-NLS-1$
    }
    List<String> command = new ArrayList<>();
    command.add(java);
    command.addAll(options.vmArguments());
    command.addAll(optionalProperties(options));
    command.add("-cp"); //$NON-NLS-1$
    command.add(classpath(classpath));
    command.add(ServerArtifacts.MAIN_CLASS);
    return List.copyOf(command);
  }

  /**
   * Returns the server's system properties whose options are set, in the order profiles, servers, clang executable.
   * The clang executable is passed only with the clang front end.
   */
  static List<String> optionalProperties(ServerOptions options) {
    List<String> properties = new ArrayList<>();
    if(!options.profiles().isEmpty()) {
      properties.add(PROFILES_PROPERTY + options.profiles());
    }
    if(!options.servers().isEmpty()) {
      properties.add(SERVERS_PROPERTY + options.servers());
    }
    if(options.clang() && !options.clangExecutable().isEmpty()) {
      properties.add(CLANG_EXECUTABLE_PROPERTY + options.clangExecutable());
    }
    return properties;
  }

  /**
   * Joins the absolute paths with the platform's path separator, keeping the first occurrence of each path.
   */
  static String classpath(List<File> files) {
    return files.stream().map(File::getAbsolutePath).distinct().collect(Collectors.joining(File.pathSeparator));
  }

  /**
   * Returns whether the Java runtime that runs Eclipse has the system Java compiler, which the server's javac front end
   * needs.
   */
  public static boolean runningJavaHasCompiler() {
    return ModuleFinder.ofSystem().find("jdk.compiler").isPresent(); //$NON-NLS-1$
  }
}
