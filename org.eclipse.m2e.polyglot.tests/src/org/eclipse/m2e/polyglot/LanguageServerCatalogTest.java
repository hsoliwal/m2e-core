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
import static org.junit.Assert.assertFalse;
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

	@Test
	public void theBundledCatalogServesTheEclipseLanguagePluginLanguages() throws IOException {
		LanguageServerCatalog catalog = LanguageServerCatalog.load();
		List<LanguageBinding> bindings = catalog.bindingsFor(
				List.of("xsltproc", "rebar3", "kotlinc", "composer", "cobc", "groovyc"),
				List.of("org.jetbrains.kotlin:kotlin-maven-plugin", "org.codehaus.gmavenplus:gmavenplus-plugin"));
		assertEquals(List.of("cobol", "php", "groovy", "kotlin", "erlang", "xml"),
				bindings.stream().map(LanguageBinding::language).toList());
		assertEquals(List.of("cobol-language-support"), commands(bindings.get(0)));
		assertEquals(List.of("phpactor", "intelephense"), commands(bindings.get(1)));
		assertEquals(List.of("groovy-language-server"), commands(bindings.get(2)));
		assertEquals(List.of("kotlin-language-server", "kotlin-lsp"), commands(bindings.get(3)));
		assertEquals(List.of("elp", "erlang_ls"), commands(bindings.get(4)));
		assertEquals(List.of("lemminx"), commands(bindings.get(5)));
		assertEquals(List.of(true, true, true, true, true, false),
				bindings.stream().map(LanguageBinding::yieldsToPlugins).toList());
		assertEquals(List.of("server"), bindings.get(4).servers().get(0).arguments());
	}

	@Test
	public void theBundledCatalogMapsMavenPluginsOnlyByTheirPluginRows() throws IOException {
		LanguageServerCatalog catalog = LanguageServerCatalog.load();
		assertTrue(catalog.knowsPlugin("org.codehaus.gmavenplus:gmavenplus-plugin"));
		assertTrue(catalog.knowsPlugin("org.jetbrains.kotlin:kotlin-maven-plugin"));
		assertFalse(catalog.knowsPlugin("org.codehaus.mojo:xml-maven-plugin"));
		assertFalse(catalog.knowsPlugin("gmavenplus-plugin"));
		assertEquals(List.of("groovy"), languages(
				catalog.bindingsFor(List.of(), List.of("org.codehaus.gmavenplus:gmavenplus-plugin", "x:y")).stream()
						.map(binding -> binding.servers().get(0)).toList()));
		assertTrue(catalog.bindingsFor(List.of("gradle"), List.of("org.apache.maven.plugins:maven-compiler-plugin"))
				.isEmpty());
	}

	@Test
	public void alternativesKeepCatalogOrderAndToolsAndPluginsShareLanguages() throws IOException {
		LanguageServerCatalog catalog = LanguageServerCatalog.parse(List.of(LanguageServerCatalog.MAGIC, //
				"tool\tphp\tphp", "plugin\tg:php-plugin\tphp\terlang", //
				"server\tphp\tfile.php\tphpactor\tlanguage-server", "server\tphp\tfile.php\tintelephense\t--stdio", //
				"server\terlang\tfile.erl\telp\tserver", "yield\terlang"));
		List<LanguageBinding> bindings = catalog.bindingsFor(List.of("php"), List.of("g:php-plugin"));
		assertEquals(2, bindings.size());
		assertEquals(new LanguageBinding("php", List.of(
				new LanguageServer("php", "file.php", "phpactor", List.of("language-server")),
				new LanguageServer("php", "file.php", "intelephense", List.of("--stdio"))), false), bindings.get(0));
		assertEquals("file.php", bindings.get(0).sampleFile());
		assertTrue(bindings.get(1).yieldsToPlugins());
		assertEquals(List.of("phpactor"), commands(catalog.serversFor(List.of("php"))));
	}

	@Test
	public void inconsistentPluginYieldAndAlternativeRowsAreRejected() {
		String m = LanguageServerCatalog.MAGIC;
		String erl = "server\terlang\tf.erl\telp";
		assertThrows(IOException.class, () -> LanguageServerCatalog.parse(List.of(m, erl, "plugin\tg:a")));
		assertThrows(IOException.class, () -> LanguageServerCatalog.parse(List.of(m, erl, "plugin\tg:a\tphp")));
		assertThrows(IOException.class,
				() -> LanguageServerCatalog.parse(List.of(m, erl, "plugin\tg:a\terlang", "plugin\tg:a\terlang")));
		assertThrows(IOException.class, () -> LanguageServerCatalog.parse(List.of(m, erl, "yield")));
		assertThrows(IOException.class, () -> LanguageServerCatalog.parse(List.of(m, erl, "yield\tphp")));
		assertThrows(IOException.class,
				() -> LanguageServerCatalog.parse(List.of(m, erl, "server\terlang\tother.erl\terlang_ls")));
		assertThrows(IOException.class,
				() -> LanguageServerCatalog.parse(List.of(m, erl, "server\terlang\tf.erl\telp\tserver")));
	}

	@Test
	public void aBindingNeedsAServer() {
		assertThrows(IllegalArgumentException.class, () -> new LanguageBinding("go", List.of(), false));
	}

	private static List<String> commands(LanguageBinding binding) {
		return commands(binding.servers());
	}

	private static List<String> commands(List<LanguageServer> servers) {
		return servers.stream().map(LanguageServer::command).toList();
	}
}
