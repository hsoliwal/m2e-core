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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;


/**
 * Settings of the code-clustering language server, and the preference keys they are read from.
 *
 * @param version the version preference, see {@link ServerArtifacts#version(String)}
 * @param javaExecutable a Java executable or a JDK directory; empty for the Java that runs Eclipse
 * @param vmArguments additional arguments of the server's JVM
 * @param minTokens the smallest atom, in logic tokens, that gets a diagnostic
 * @param profiles the server's {@code clustering.lsp.profiles}: the language cores (for example
 *          {@code jdt,cdt,pydev,wtp,rust} or {@code all}) whose language servers split files into atoms; empty for the
 *          built-in front ends
 * @param servers the server's {@code clustering.lsp.servers}: explicit extension to command mappings; empty for none
 * @param classpath an explicit server class path; when it is not empty, nothing is resolved
 * @param clang whether C, C++ and Objective-C files are atomized on clang's syntax tree: the artifact
 *          {@code maven-code-clustering-clang} joins the server class path (clang and a compile_commands.json are
 *          needed)
 * @param clangExecutable the clang to run; empty for {@code clang} on PATH or in the LLVM folders
 */
public record ServerOptions(String version, String javaExecutable, List<String> vmArguments, int minTokens,
    String profiles, String servers, List<String> classpath, boolean clang, String clangExecutable) {

  public static final String ENABLED = "enabled"; //$NON-NLS-1$

  public static final String VERSION = "version"; //$NON-NLS-1$

  public static final String JAVA = "java"; //$NON-NLS-1$

  public static final String VM_ARGUMENTS = "vmArguments"; //$NON-NLS-1$

  public static final String MIN_TOKENS = "minTokens"; //$NON-NLS-1$

  public static final String PROFILES = "profiles"; //$NON-NLS-1$

  public static final String SERVERS = "servers"; //$NON-NLS-1$

  public static final String CLASSPATH = "classpath"; //$NON-NLS-1$

  public static final String CLANG = "clang"; //$NON-NLS-1$

  public static final String CLANG_EXECUTABLE = "clangExecutable"; //$NON-NLS-1$

  public static final int DEFAULT_MIN_TOKENS = 12;

  public ServerOptions {
    vmArguments = List.copyOf(vmArguments);
    classpath = List.copyOf(classpath);
    clangExecutable = clangExecutable == null ? "" : clangExecutable.strip(); //$NON-NLS-1$
  }

  /** Settings without the clang front end. */
  public ServerOptions(String version, String javaExecutable, List<String> vmArguments, int minTokens, String profiles,
      String servers, List<String> classpath) {
    this(version, javaExecutable, vmArguments, minTokens, profiles, servers, classpath, false, ""); //$NON-NLS-1$
  }

  /**
   * Reads the settings through a preference lookup; a key without a value reads as empty.
   */
  public static ServerOptions read(UnaryOperator<String> preferences) {
    return new ServerOptions(text(preferences, VERSION), text(preferences, JAVA),
        arguments(text(preferences, VM_ARGUMENTS)), minTokens(text(preferences, MIN_TOKENS)),
        text(preferences, PROFILES), text(preferences, SERVERS), paths(text(preferences, CLASSPATH)),
        Boolean.parseBoolean(text(preferences, CLANG)), text(preferences, CLANG_EXECUTABLE));
  }

  /**
   * Returns the value as an integer of at least 1, or {@link #DEFAULT_MIN_TOKENS} for anything else.
   */
  static int minTokens(String value) {
    try {
      int tokens = Integer.parseInt(value.strip());
      return tokens >= 1 ? tokens : DEFAULT_MIN_TOKENS;
    } catch(NumberFormatException ex) {
      return DEFAULT_MIN_TOKENS;
    }
  }

  /**
   * Splits JVM arguments at white space. Double quotes keep white space inside one argument and are removed; an
   * unclosed quote runs to the end, and {@code ""} is an empty argument.
   */
  static List<String> arguments(String value) {
    List<String> arguments = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean quoted = false;
    boolean started = false;
    for(char c : value.toCharArray()) {
      if(c == '"') {
        quoted = !quoted;
        started = true;
      } else if(Character.isWhitespace(c) && !quoted) {
        if(started) {
          arguments.add(current.toString());
          current.setLength(0);
          started = false;
        }
      } else {
        current.append(c);
        started = true;
      }
    }
    if(started) {
      arguments.add(current.toString());
    }
    return arguments;
  }

  /**
   * Splits a class path at the platform's path separator and at line breaks, and drops blank entries.
   */
  static List<String> paths(String value) {
    return Arrays.stream(value.split("[\\r\\n" + File.pathSeparator + "]+")).map(String::strip) //$NON-NLS-1$ //$NON-NLS-2$
        .filter(path -> !path.isEmpty()).toList();
  }

  private static String text(UnaryOperator<String> preferences, String key) {
    String value = preferences.apply(key);
    return value == null ? "" : value.strip(); //$NON-NLS-1$
  }
}
