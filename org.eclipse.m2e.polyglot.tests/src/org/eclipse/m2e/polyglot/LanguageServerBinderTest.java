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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.lsp4e.ContentTypeToLSPLaunchConfigEntry;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.junit.After;
import org.junit.Assume;
import org.junit.Test;

public class LanguageServerBinderTest {

	private static final LanguageServer RUST = new LanguageServer("rust", "file.rs", "rust-analyzer-test", List.of());

	private Path executable;

	@After
	public void removeAssociationsAndLaunchConfigurations() throws CoreException, IOException {
		LanguageServersRegistry registry = LanguageServersRegistry.getInstance();
		List<ContentTypeToLSPLaunchConfigEntry> kept = registry.getContentTypeToLSPLaunches().stream()
				.filter(entry -> !entry.getLaunchConfiguration().getName().startsWith(LanguageServerBinder.NAME_PREFIX))
				.toList();
		registry.setAssociations(kept);
		ILaunchManager launches = DebugPlugin.getDefault().getLaunchManager();
		for (ILaunchConfiguration configuration : launches.getLaunchConfigurations()) {
			if (configuration.getName().startsWith(LanguageServerBinder.NAME_PREFIX)) {
				configuration.delete();
			}
		}
		if (executable != null) {
			Files.deleteIfExists(executable);
		}
	}

	@Test
	public void theBundleDeclaresLowPriorityContentTypes() {
		IContentType rust = Platform.getContentTypeManager().findContentTypeFor("file.rs");
		assertEquals("org.eclipse.m2e.polyglot.rust", rust.getId());
		assertEquals("org.eclipse.m2e.polyglot.cpp",
				Platform.getContentTypeManager().findContentTypeFor("file.hpp").getId());
	}

	@Test
	public void aServerOnThePathIsBoundOnceAndThenServed() throws CoreException, IOException {
		executable = Files.createTempFile("rust-analyzer-test", ".exe");
		LanguageServerBinder binder = new LanguageServerBinder();
		IContentType rust = Platform.getContentTypeManager().findContentTypeFor("file.rs");
		assertFalse(LanguageServerBinder.served(rust));

		assertEquals(BindOutcome.BOUND, binder.bind(RUST, command -> Optional.of(executable)));

		assertTrue(LanguageServerBinder.served(rust));
		ContentTypeToLSPLaunchConfigEntry entry = LanguageServersRegistry.getInstance().getContentTypeToLSPLaunches()
				.stream().filter(e -> e.getKey().equals(rust)).findFirst().orElseThrow();
		ILaunchConfiguration configuration = entry.getLaunchConfiguration();
		assertEquals(LanguageServerBinder.NAME_PREFIX + "rust-analyzer-test", configuration.getName());
		assertEquals(LanguageServerBinder.PROGRAM_TYPE, configuration.getType().getIdentifier());
		assertEquals(executable.toAbsolutePath().toString(),
				configuration.getAttribute(LanguageServerBinder.ATTR_LOCATION, ""));
		assertEquals(BindOutcome.ALREADY_SERVED, binder.bind(RUST, command -> Optional.of(executable)));
	}

	@Test
	public void missingServersAndUnknownFilesAreReportedNotBound() throws CoreException {
		LanguageServerBinder binder = new LanguageServerBinder();
		LanguageServer go = new LanguageServer("go", "file.go", "gopls-test", List.of());
		assertEquals(BindOutcome.NOT_ON_PATH, binder.bind(go, command -> Optional.empty()));
		LanguageServer unknown = new LanguageServer("x", "file.no-such-extension", "x-test", List.of());
		assertEquals(BindOutcome.NO_CONTENT_TYPE, binder.bind(unknown, command -> Optional.of(Path.of("x"))));
		assertFalse(LanguageServerBinder.served(Platform.getContentTypeManager().findContentTypeFor("file.go")));
	}

	@Test
	public void theEclipseLanguagePluginLanguagesHaveLowPriorityContentTypes() {
		for (String language : List.of("groovy", "kotlin", "php", "cobol", "erlang")) {
			String sample = sample(language);
			assertEquals(sample, LanguageServerBinder.OWN_CONTENT_TYPES + language,
					Platform.getContentTypeManager().findContentTypeFor(sample).getId());
		}
		assertEquals("org.eclipse.core.runtime.xml",
				Platform.getContentTypeManager().findContentTypeFor("file.xml").getId());
	}

	@Test
	public void theFirstAlternativeOnThePathIsBound() throws CoreException, IOException {
		executable = Files.createTempFile("kotlin-lsp-test", ".exe");
		LanguageBinding kotlin = new LanguageBinding("kotlin",
				List.of(new LanguageServer("kotlin", "file.kt", "kotlin-language-server-test", List.of()),
						new LanguageServer("kotlin", "file.kt", "kotlin-lsp-test", List.of("--stdio"))),
				true);
		Map<String, Path> onPath = Map.of("kotlin-lsp-test", executable);

		assertEquals(BindOutcome.BOUND, new LanguageServerBinder().bind(kotlin, c -> Optional.ofNullable(onPath.get(c))));

		IContentType contentType = Platform.getContentTypeManager().findContentTypeFor("file.kt");
		ILaunchConfiguration configuration = LanguageServersRegistry.getInstance().getContentTypeToLSPLaunches()
				.stream().filter(e -> e.getKey().equals(contentType)).findFirst().orElseThrow().getLaunchConfiguration();
		assertEquals(LanguageServerBinder.NAME_PREFIX + "kotlin-lsp-test", configuration.getName());
		assertEquals("--stdio", configuration.getAttribute(LanguageServerBinder.ATTR_ARGUMENTS, ""));
	}

	@Test
	public void aYieldingLanguageLeavesAnotherPluginsContentTypeAlone() throws CoreException {
		IContentType xml = Platform.getContentTypeManager().findContentTypeFor("file.xml");
		Assume.assumeFalse("an LSP4E server already serves XML", LanguageServerBinder.served(xml));
		LanguageBinding yielding = new LanguageBinding("xml",
				List.of(new LanguageServer("xml", "file.xml", "lemminx-test", List.of())), true);
		List<String> looked = new ArrayList<>();

		assertEquals(BindOutcome.IDE_PLUGIN, new LanguageServerBinder().bind(yielding, command -> {
			looked.add(command);
			return Optional.of(Path.of(command));
		}));

		assertEquals(List.of(), looked);
		assertFalse(LanguageServerBinder.served(xml));
	}

	private static String sample(String language) {
		return switch (language) {
		case "kotlin" -> "file.kt";
		case "cobol" -> "file.cbl";
		case "erlang" -> "file.erl";
		default -> "file." + language;
		};
	}
}
