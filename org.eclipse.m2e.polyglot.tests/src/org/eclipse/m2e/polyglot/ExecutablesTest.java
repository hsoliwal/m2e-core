/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0, or (at your option) the Eclipse
 * Public License 2.0, which are available at
 * https://www.apache.org/licenses/LICENSE-2.0 and
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.m2e.polyglot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class ExecutablesTest {

	private Path first;
	private Path second;

	@Before
	public void createFolders() throws IOException {
		first = Files.createTempDirectory("executables-first");
		second = Files.createTempDirectory("executables-second");
	}

	@After
	public void deleteFolders() throws IOException {
		for (Path folder : new Path[] { first, second }) {
			try (Stream<Path> files = Files.walk(folder)) {
				for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
					Files.delete(file);
				}
			}
		}
	}

	@Test
	public void windowsTriesThePathExtensionsInOrderAcrossTheFolders() throws IOException {
		Files.createFile(first.resolve("npm"));
		Path cmd = Files.createFile(second.resolve("npm.cmd"));
		String path = first + ";;" + second;
		assertEquals(Optional.of(cmd), Executables.find("npm", path, ".EXE;.CMD", true));
		Path exe = Files.createFile(second.resolve("npm.exe"));
		assertEquals(Optional.of(exe), Executables.find("npm", path, ".EXE;.CMD", true));
		assertEquals(Optional.of(cmd), Executables.find("npm.cmd", path, ".EXE;.CMD", true));
		assertEquals(Optional.empty(), Executables.find("npm", path, ".BAT", true));
	}

	@Test
	public void otherSystemsNeedAnExecutableFileWithTheExactName() throws IOException {
		Assume.assumeTrue("':' separates PATH entries only where paths have no drive letters",
				File.pathSeparatorChar == ':');
		Path tool = Files.createFile(second.resolve("gopls"));
		String path = first + ":" + second;
		Files.createDirectory(first.resolve("gopls"));
		boolean marked = tool.toFile().setExecutable(true);
		assertEquals(Optional.of(tool), Executables.find("gopls", path, "", false));
		if (marked && tool.toFile().setExecutable(false) && !Files.isExecutable(tool)) {
			assertEquals(Optional.empty(), Executables.find("gopls", path, "", false));
		}
		assertEquals(Optional.empty(), Executables.find("gopls.exe", path, "", false));
	}

	@Test
	public void aCommandWithAFolderIsAPathAndABlankCommandIsNothing() throws IOException {
		Path tool = Files.createFile(first.resolve("clangd"));
		assertEquals(Optional.of(tool), Executables.find(tool.toString(), "", "", true));
		assertEquals(Optional.empty(), Executables.find(first.resolve("missing").toString(), first.toString(), "", false));
		assertEquals(Optional.empty(), Executables.find(" ", first.toString(), ".EXE", true));
		assertTrue(Executables.find("no-such-command-" + System.nanoTime()).isEmpty());
	}
}
