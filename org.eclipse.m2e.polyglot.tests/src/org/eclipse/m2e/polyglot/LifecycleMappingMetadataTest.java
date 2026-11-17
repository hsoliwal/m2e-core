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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.m2e.core.internal.lifecyclemapping.model.LifecycleMappingMetadataSource;
import org.eclipse.m2e.core.internal.lifecyclemapping.model.PluginExecutionMetadata;
import org.eclipse.m2e.core.internal.lifecyclemapping.model.io.xpp3.LifecycleMappingMetadataSourceXpp3Reader;
import org.eclipse.m2e.core.lifecyclemapping.model.PluginExecutionAction;
import org.eclipse.m2e.core.project.configurator.MojoExecutionKey;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * The bundle's lifecycle mapping metadata, read with m2e's own reader and matched with m2e's own filters, against
 * the catalog and the configurators and content types of {@code plugin.xml}.
 */
public class LifecycleMappingMetadataTest {

	static final String LANGUAGE_PLUGINS = "org.eclipse.m2e.polyglot.languagePlugins";

	private static final String GMAVENPLUS = "org.codehaus.gmavenplus:gmavenplus-plugin";

	private static final String KOTLIN = "org.jetbrains.kotlin:kotlin-maven-plugin";

	@Test
	public void everyExecutionGoesToTheLanguagePluginConfigurator() throws Exception {
		List<PluginExecutionMetadata> executions = metadata().getPluginExecutions();
		assertEquals(3, executions.size());
		for (PluginExecutionMetadata execution : executions) {
			assertEquals(PluginExecutionAction.configurator, execution.getAction());
			assertEquals(LANGUAGE_PLUGINS, execution.getConfiguration().getChild("id").getValue());
		}
	}

	@Test
	public void theLifecycleGoalsOfGMavenPlusAndKotlinAreMapped() throws Exception {
		for (String goal : List.of("addSources", "addTestSources", "generateStubs", "generateTestStubs", "compile",
				"compileTests", "removeStubs", "removeTestStubs")) {
			assertTrue(goal, mapped(GMAVENPLUS, "4.2.1", goal));
		}
		assertTrue(mapped(GMAVENPLUS, "1.5", "testCompile"));
		assertFalse(mapped(GMAVENPLUS, "1.5", "compileTests"));
		assertFalse(mapped(GMAVENPLUS, "4.2.1", "testCompile"));
		assertFalse(mapped(GMAVENPLUS, "4.2.1", "execute"));
		for (String goal : List.of("compile", "test-compile", "kapt", "test-kapt")) {
			assertTrue(goal, mapped(KOTLIN, "2.2.21", goal));
		}
		assertFalse(mapped(KOTLIN, "2.2.21", "js"));
	}

	@Test
	public void pluginsWithTheirOwnMappingAreLeftAlone() throws Exception {
		assertFalse(mapped("org.codehaus.mojo:xml-maven-plugin", "1.2.0", "validate"));
		assertFalse(mapped("org.codehaus.mojo:xml-maven-plugin", "1.2.0", "transform"));
		assertFalse(mapped("org.codehaus.groovy:groovy-eclipse-compiler", "3.9.0", "add-groovy-build-paths"));
		assertFalse(mapped("org.apache.maven.plugins:maven-compiler-plugin", "3.14.0", "compile"));
	}

	@Test
	public void theMappedPluginsAreExactlyTheCatalogsPluginRows() throws Exception {
		Set<String> mapped = metadata().getPluginExecutions().stream()
				.map(e -> e.getFilter().getGroupId() + ":" + e.getFilter().getArtifactId())
				.collect(Collectors.toCollection(TreeSet::new));
		Set<String> rows = catalogRows("plugin").map(fields -> fields[1])
				.collect(Collectors.toCollection(TreeSet::new));
		assertEquals(Set.of(GMAVENPLUS, KOTLIN), mapped);
		assertEquals(mapped, rows);
	}

	@Test
	public void theLanguagePluginConfiguratorRunsAfterTheIdeConfiguratorsOnlyWhenTheyAreInstalled() throws Exception {
		Element configurator = element(plugin(), "configurator", "id", LANGUAGE_PLUGINS);
		assertEquals(PolyglotProjectConfigurator.class.getName(), configurator.getAttribute("class"));
		List<String> runsAfter = List.of(configurator.getAttribute("runsAfter").split(","));
		assertEquals(List.of("org.codehaus.groovy.m2eclipse.configurator?",
				"org.jetbrains.kotlin.maven.projectConfigurator?"), runsAfter);
		assertNotNull(element(plugin(), "extension", "point", "org.eclipse.m2e.core.lifecycleMappingMetadataSource"));
	}

	@Test
	public void everyYieldingLanguageHasALowPriorityContentTypeForItsSampleFile() throws Exception {
		Document plugin = plugin();
		LanguageServerCatalog catalog = LanguageServerCatalog.load();
		List<String> yielding = catalogRows("yield").flatMap(fields -> Stream.of(fields).skip(1)).toList();
		assertEquals(List.of("groovy", "kotlin", "php", "cobol", "erlang"), yielding);
		for (LanguageBinding binding : catalog.bindingsFor(List.of(), List.of(GMAVENPLUS, KOTLIN))) {
			assertTrue(binding.language(), binding.yieldsToPlugins());
		}
		for (String language : yielding) {
			Element type = element(plugin, "content-type", "id", LanguageServerBinder.OWN_CONTENT_TYPES + language);
			assertEquals("low", type.getAttribute("priority"));
			String sample = catalogRows("server").filter(f -> f[1].equals(language)).findFirst().orElseThrow()[2];
			List<String> extensions = List.of(type.getAttribute("file-extensions").split(","));
			assertTrue(sample, extensions.contains(sample.substring(sample.lastIndexOf('.') + 1)));
			assertNotNull(element(plugin, "editorContentTypeBinding", "contentTypeId", type.getAttribute("id")));
		}
	}

	private static boolean mapped(String plugin, String version, String goal) throws Exception {
		String[] coordinates = plugin.split(":");
		MojoExecutionKey key = new MojoExecutionKey(coordinates[0], coordinates[1], version, goal, "compile", "default");
		return metadata().getPluginExecutions().stream().anyMatch(execution -> execution.getFilter().match(key));
	}

	private static LifecycleMappingMetadataSource metadata() throws Exception {
		try (InputStream stream = resource("/lifecycle-mapping-metadata.xml")) {
			return new LifecycleMappingMetadataSourceXpp3Reader().read(stream, true);
		}
	}

	private static Document plugin() throws Exception {
		try (InputStream stream = resource("/plugin.xml")) {
			return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream);
		}
	}

	private static Element element(Document document, String tag, String attribute, String value) {
		var nodes = document.getElementsByTagName(tag);
		return IntStream.range(0, nodes.getLength()).mapToObj(i -> (Element) nodes.item(i))
				.filter(e -> value.equals(e.getAttribute(attribute))).findFirst()
				.orElseThrow(() -> new AssertionError("no " + tag + " with " + attribute + "=" + value));
	}

	private static Stream<String[]> catalogRows(String kind) throws Exception {
		try (InputStream stream = resource(LanguageServerCatalog.RESOURCE)) {
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
			return reader.lines().map(line -> line.split("\t", -1)).filter(fields -> fields[0].equals(kind)).toList()
					.stream();
		}
	}

	private static InputStream resource(String name) {
		InputStream stream = PolyglotProjectConfigurator.class.getResourceAsStream(name);
		assertNotNull(name, stream);
		return stream;
	}
}
