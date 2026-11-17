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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;


public class ServerOptionsTest {

  @Test
  public void testMissingPreferencesReadAsDefaults() {
    ServerOptions options = ServerOptions.read(key -> null);
    assertEquals("", options.version());
    assertEquals("", options.javaExecutable());
    assertEquals(List.of(), options.vmArguments());
    assertEquals(ServerOptions.DEFAULT_MIN_TOKENS, options.minTokens());
    assertEquals("", options.profiles());
    assertEquals("", options.servers());
    assertEquals(List.of(), options.classpath());
  }

  @Test
  public void testPreferencesAreReadByKey() {
    Map<String, String> preferences = Map.of(ServerOptions.VERSION, " 0.2.0 ", ServerOptions.JAVA, "/opt/jdk",
        ServerOptions.VM_ARGUMENTS, "-Xmx1g -ea", ServerOptions.MIN_TOKENS, "20", ServerOptions.PROFILES,
        "jdt,cdt,pydev,wtp,rust", ServerOptions.SERVERS, "go=gopls", ServerOptions.CLASSPATH,
        "a.jar" + File.pathSeparator + "b.jar");
    ServerOptions options = ServerOptions.read(preferences::get);
    assertEquals("0.2.0", options.version());
    assertEquals("/opt/jdk", options.javaExecutable());
    assertEquals(List.of("-Xmx1g", "-ea"), options.vmArguments());
    assertEquals(20, options.minTokens());
    assertEquals("jdt,cdt,pydev,wtp,rust", options.profiles());
    assertEquals("go=gopls", options.servers());
    assertEquals(List.of("a.jar", "b.jar"), options.classpath());
  }

  @Test
  public void testMinTokensBelowOneOrNotANumberGiveDefault() {
    assertEquals(7, ServerOptions.minTokens(" 7 "));
    assertEquals(1, ServerOptions.minTokens("1"));
    assertEquals(ServerOptions.DEFAULT_MIN_TOKENS, ServerOptions.minTokens("0"));
    assertEquals(ServerOptions.DEFAULT_MIN_TOKENS, ServerOptions.minTokens("-3"));
    assertEquals(ServerOptions.DEFAULT_MIN_TOKENS, ServerOptions.minTokens("twelve"));
    assertEquals(ServerOptions.DEFAULT_MIN_TOKENS, ServerOptions.minTokens(""));
    assertEquals(ServerOptions.DEFAULT_MIN_TOKENS, ServerOptions.minTokens("99999999999"));
  }

  @Test
  public void testArgumentsKeepQuotedWhiteSpace() {
    assertEquals(List.of("-Xmx1g", "-Dpath=C:/Program Files/x", "-ea"),
        ServerOptions.arguments("  -Xmx1g \"-Dpath=C:/Program Files/x\"\t-ea "));
    assertEquals(List.of("-Da=", "b"), ServerOptions.arguments("-Da=\"\" b"));
    assertEquals(List.of(""), ServerOptions.arguments("\"\""));
    assertEquals(List.of("-Dx=open end"), ServerOptions.arguments("\"-Dx=open end"));
    assertEquals(List.of(), ServerOptions.arguments(" \n "));
  }

  @Test
  public void testPathsSplitAtSeparatorAndLineBreaks() {
    String value = "a.jar" + File.pathSeparator + File.pathSeparator + " b.jar \r\nc.jar\n\n";
    assertEquals(List.of("a.jar", "b.jar", "c.jar"), ServerOptions.paths(value));
    assertEquals(List.of(), ServerOptions.paths(""));
  }

  @Test
  public void testListsAreCopied() {
    List<String> arguments = new ArrayList<>(List.of("-ea"));
    ServerOptions options = new ServerOptions("", "", arguments, 12, "", "", List.of());
    arguments.add("-Xmx1g");
    assertEquals(List.of("-ea"), options.vmArguments());
    assertThrows(UnsupportedOperationException.class, () -> options.vmArguments().add("x"));
  }
}
