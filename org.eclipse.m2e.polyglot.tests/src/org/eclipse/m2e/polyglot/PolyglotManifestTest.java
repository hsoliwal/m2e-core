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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class PolyglotManifestTest {

	@Test
	public void taskRowsGiveNormalisedToolsAndOtherRowsAreIgnored() throws IOException {
		Set<String> tools = PolyglotManifest.tools(List.of("M3-POLYGLOT-TASKS-V1\r", //
				"# comment", "", //
				"task\tcargo-compile\tcargo\t.\r", //
				"arg\tcargo-compile\tbuild", //
				"task\tnpm-test\tC:\\tools\\NPM.CMD\tweb", //
				"task\tmake-compile\t./bin/make\tnative", //
				"requires\tnpm-test\tcargo-compile", //
				"input\tmake-compile\tsrc"));
		assertEquals(List.of("cargo", "make", "npm"), List.copyOf(tools));
		assertThrows(UnsupportedOperationException.class, () -> tools.add("x"));
	}

	@Test
	public void theMagicLineIsRequired() {
		assertThrows(IOException.class, () -> PolyglotManifest.tools(List.of()));
		assertThrows(IOException.class, () -> PolyglotManifest.tools(List.of("task\ta\tcargo\t.")));
		assertThrows(IOException.class, () -> PolyglotManifest.tools(List.of("M3-POLYGLOT-TASKS-V2")));
	}

	@Test
	public void malformedTaskRowsAreRejected() {
		assertThrows(IOException.class,
				() -> PolyglotManifest.tools(List.of(PolyglotManifest.MAGIC, "task\ta\tcargo")));
		assertThrows(IOException.class,
				() -> PolyglotManifest.tools(List.of(PolyglotManifest.MAGIC, "task\ta\tcargo\t.\textra")));
		assertThrows(IOException.class, () -> PolyglotManifest.tools(List.of(PolyglotManifest.MAGIC, "task\ta\t \t.")));
	}

	@Test
	public void toolNamesDropFoldersCaseAndOneWindowsExtension() {
		assertEquals("gradlew", PolyglotManifest.toolName("./gradlew"));
		assertEquals("cmake", PolyglotManifest.toolName("C:\\Program Files\\CMake\\bin\\cmake.exe"));
		assertEquals("yarn", PolyglotManifest.toolName("YARN.cmd"));
		assertEquals("python3", PolyglotManifest.toolName("/usr/bin/python3"));
		assertEquals("tool.exe", PolyglotManifest.toolName("tool.exe.bat"));
	}

	@Test
	public void aMissingFileHasNoToolsAndAFileIsRead() throws IOException {
		Path folder = Files.createTempDirectory("polyglot-manifest");
		try {
			assertTrue(PolyglotManifest.tools(folder.resolve("build.tsv")).isEmpty());
			Path manifest = Files.writeString(folder.resolve("build.tsv"),
					PolyglotManifest.MAGIC + "\ntask\tgo-compile\tgo\t.\n");
			assertEquals(Set.of("go"), PolyglotManifest.tools(manifest));
		} finally {
			Files.deleteIfExists(folder.resolve("build.tsv"));
			Files.delete(folder);
		}
	}
}
