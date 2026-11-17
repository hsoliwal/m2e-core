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
import java.util.List;

import org.junit.Test;

public class LanguageServerCatalogTest {

	private static List<String> languages(List<LanguageServer> servers) {
		return servers.stream().map(LanguageServer::language).toList();
	}

	@Test
	public void theBundledCatalogLoadsAndMapsTheMavenizerTools() throws IOException {
		LanguageServerCatalog catalog = LanguageServerCatalog.load();
		assertEquals(List.of("rust", "c", "cpp"), languages(catalog.serversFor(List.of("gradle", "cmake", "cargo"))));
		assertEquals(List.of("javascript", "typescript"), languages(catalog.serversFor(List.of("pnpm", "npm"))));
		assertTrue(catalog.serversFor(List.of("ant", "bazel", "gradlew")).isEmpty());
		LanguageServer typescript = catalog.serversFor(List.of("bun")).get(1);
		assertEquals(new LanguageServer("typescript", "file.ts", "typescript-language-server", List.of("--stdio")),
				typescript);
	}

	@Test
	public void rowsAreParsedInOrderWithArguments() throws IOException {
		LanguageServerCatalog catalog = LanguageServerCatalog.parse(List.of(LanguageServerCatalog.MAGIC + "\r", //
				"# comment", "", //
				"tool\tmake\tcpp\tc", //
				"server\tc\tfile.c\tclangd\r", //
				"server\tcpp\tfile.cpp\tclangd\t--log=error\t--pretty"));
		assertEquals(List.of(new LanguageServer("cpp", "file.cpp", "clangd", List.of("--log=error", "--pretty")),
				new LanguageServer("c", "file.c", "clangd", List.of())), catalog.serversFor(List.of("make", "make")));
	}

	@Test
	public void inconsistentCatalogsAreRejected() {
		String m = LanguageServerCatalog.MAGIC;
		assertThrows(IOException.class, () -> LanguageServerCatalog.parse(List.of()));
		assertThrows(IOException.class, () -> LanguageServerCatalog.parse(List.of("tool\tgo\tgo")));
		assertThrows(IOException.class,
				() -> LanguageServerCatalog.parse(List.of(m, "tool\tgo\tgo", "tool\tgo\tgo", "server\tgo\tf.go\tgopls")));
		assertThrows(IOException.class,
				() -> LanguageServerCatalog.parse(List.of(m, "server\tgo\tf.go\tgopls", "server\tgo\tf.go\tgopls")));
		assertThrows(IOException.class, () -> LanguageServerCatalog.parse(List.of(m, "tool\tgo\tgo")));
		assertThrows(IOException.class, () -> LanguageServerCatalog.parse(List.of(m, "tool\tgo")));
		assertThrows(IOException.class, () -> LanguageServerCatalog.parse(List.of(m, "server\tgo\tf.go")));
		assertThrows(IOException.class, () -> LanguageServerCatalog.parse(List.of(m, "editor\tgo\tf.go\tgopls")));
	}
}
