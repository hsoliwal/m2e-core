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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;


public class ServerCommandLineTest {

  @Rule
  public TemporaryFolder temporary = new TemporaryFolder();

  private static ServerOptions options(List<String> vmArguments, String profiles, String servers) {
    return new ServerOptions("", "", vmArguments, 12, profiles, servers, List.of());
  }

  @Test
  public void testMinimalCommand() {
    File lsp = new File("lsp.jar");
    File plugin = new File("plugin.jar");
    assertEquals(List.of("java", "-cp", lsp.getAbsolutePath() + File.pathSeparator + plugin.getAbsolutePath(),
        "org.apache.maven.plugins.clustering.lsp.ClusteringServerMain"),
        ServerCommandLine.build(options(List.of(), "", ""), "java", List.of(lsp, plugin)));
  }

  @Test
  public void testVmArgumentsAndServerPropertiesComeBeforeTheClassPath() {
    List<String> command = ServerCommandLine.build(
        options(List.of("-Xmx1g", "-ea"), "jdt,cdt,pydev,wtp,rust", "c,h='C:/Program Files/LLVM/bin/clangd.exe';rs=rust-analyzer"),
        "/jdk/bin/java", List.of(new File("a.jar")));
    assertEquals(List.of("/jdk/bin/java", "-Xmx1g", "-ea", "-Dclustering.lsp.profiles=jdt,cdt,pydev,wtp,rust",
        "-Dclustering.lsp.servers=c,h='C:/Program Files/LLVM/bin/clangd.exe';rs=rust-analyzer", "-cp",
        new File("a.jar").getAbsolutePath(), ServerArtifacts.MAIN_CLASS), command);
  }

  @Test
  public void testClassPathKeepsFirstOccurrence() {
    File a = new File("a.jar");
    File b = new File("b.jar");
    assertEquals(a.getAbsolutePath() + File.pathSeparator + b.getAbsolutePath(),
        ServerCommandLine.classpath(List.of(a, b, new File("a.jar"))));
  }

  @Test
  public void testEmptyClassPathIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> ServerCommandLine.build(options(List.of(), "", ""), "java", List.of()));
  }

  @Test
  public void testJavaOfTheRunningJvm() {
    assertEquals(Path.of("/jre", "bin", "java").toString(), ServerCommandLine.javaExecutable("", "/jre", false));
    assertEquals(Path.of("/jre", "bin", "java.exe").toString(), ServerCommandLine.javaExecutable(null, "/jre", true));
  }

  @Test
  public void testJavaPreferenceNamesAJdkOrAnExecutable() throws IOException {
    Path jdk = temporary.newFolder("jdk").toPath();
    assertEquals(jdk.resolve("bin").resolve("java").toString(),
        ServerCommandLine.javaExecutable(" " + jdk + " ", "/jre", false));
    Path executable = Files.createFile(jdk.resolve("java-21"));
    assertEquals(executable.toString(), ServerCommandLine.javaExecutable(executable.toString(), "/jre", true));
  }
}
