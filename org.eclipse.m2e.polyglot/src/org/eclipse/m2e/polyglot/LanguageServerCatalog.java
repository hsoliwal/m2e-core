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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Which languages a tool or a Maven plug-in builds and which language servers serve each language, read from
 * {@value #RESOURCE} (format {@value #MAGIC}), one row per line, fields separated by tabs:
 * <ul>
 * <li>{@code tool <tool> <language>...}: a tool of a polyglot task manifest;</li>
 * <li>{@code plugin <groupId>:<artifactId> <language>...}: a Maven plug-in whose executions the bundle's lifecycle
 * mapping metadata maps to the language plug-in configurator;</li>
 * <li>{@code server <language> <sample file> <command> <argument>...}: a server; several rows for one language are
 * alternatives in order of preference, with the same sample file;</li>
 * <li>{@code yield <language>...}: languages bound only to this bundle's own content type.</li>
 * </ul>
 */
final class LanguageServerCatalog {

	static final String MAGIC = "M2E-POLYGLOT-LANGUAGE-SERVERS-V1";

	static final String RESOURCE = "/language-servers.tsv";

	private final Map<String, List<String>> languagesByTool;

	private final Map<String, List<String>> languagesByPlugin;

	private final Map<String, List<LanguageServer>> serversByLanguage;

	private final Set<String> yielding;

	private LanguageServerCatalog(Rows rows) {
		this.languagesByTool = Collections.unmodifiableMap(rows.languagesByTool);
		this.languagesByPlugin = Collections.unmodifiableMap(rows.languagesByPlugin);
		this.serversByLanguage = Collections.unmodifiableMap(rows.serversByLanguage);
		this.yielding = Collections.unmodifiableSet(rows.yielding);
	}

	static LanguageServerCatalog parse(List<String> lines) throws IOException {
		if (lines.isEmpty() || !MAGIC.equals(stripCarriageReturn(lines.get(0)))) {
			throw new IOException("not a language server catalog");
		}
		Rows rows = new Rows();
		for (String raw : lines.subList(1, lines.size())) {
			String line = stripCarriageReturn(raw);
			if (!line.isBlank() && !line.startsWith("#")) {
				rows.read(line);
			}
		}
		rows.requireServers();
		return new LanguageServerCatalog(rows);
	}

	static LanguageServerCatalog load() throws IOException {
		try (InputStream stream = LanguageServerCatalog.class.getResourceAsStream(RESOURCE)) {
			if (stream == null) {
				throw new IOException("missing " + RESOURCE);
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
			return parse(reader.lines().toList());
		}
	}

	/** The preferred server of each language of the tools: the first server of each {@link #bindingsFor} binding. */
	List<LanguageServer> serversFor(Collection<String> tools) {
		return bindingsFor(tools, List.of()).stream().map(binding -> binding.servers().get(0)).toList();
	}

	/**
	 * The bindings of the languages of the tools, then of the Maven plug-ins ({@code groupId:artifactId}), each language
	 * once: tools and plug-ins in natural order, languages in catalog order.
	 */
	List<LanguageBinding> bindingsFor(Collection<String> tools, Collection<String> plugins) {
		return Stream.concat(languages(languagesByTool, tools), languages(languagesByPlugin, plugins)).distinct()
				.map(language -> new LanguageBinding(language, serversByLanguage.get(language),
						yielding.contains(language)))
				.toList();
	}

	/** True when a {@code plugin} row names the Maven plug-in ({@code groupId:artifactId}). */
	boolean knowsPlugin(String plugin) {
		return languagesByPlugin.containsKey(plugin);
	}

	private static Stream<String> languages(Map<String, List<String>> languagesByKey, Collection<String> keys) {
		return new TreeSet<>(keys).stream().flatMap(key -> languagesByKey.getOrDefault(key, List.of()).stream());
	}

	private static String stripCarriageReturn(String line) {
		return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
	}

	/** The rows read so far, in catalog order. */
	private static final class Rows {

		final Map<String, List<String>> languagesByTool = new LinkedHashMap<>();

		final Map<String, List<String>> languagesByPlugin = new LinkedHashMap<>();

		final Map<String, List<LanguageServer>> serversByLanguage = new LinkedHashMap<>();

		final Set<String> yielding = new LinkedHashSet<>();

		void read(String line) throws IOException {
			String[] fields = line.split("\t", -1);
			switch (fields[0]) {
			case "tool" -> put(languagesByTool, "tool", fields, line);
			case "plugin" -> put(languagesByPlugin, "plugin", fields, line);
			case "server" -> server(fields, line);
			case "yield" -> yielding.addAll(List.of(tail(fields, 1, 2, line)));
			default -> throw malformed(line);
			}
		}

		void requireServers() throws IOException {
			requireServers("tool", languagesByTool);
			requireServers("plugin", languagesByPlugin);
			requireServers("yield", Map.of("row", List.copyOf(yielding)));
		}

		private static void put(Map<String, List<String>> languagesByKey, String kind, String[] fields, String line)
				throws IOException {
			List<String> languages = List.of(tail(fields, 2, 3, line));
			if (languagesByKey.putIfAbsent(fields[1], languages) != null) {
				throw new IOException("duplicate " + kind + ": " + fields[1]);
			}
		}

		private void server(String[] fields, String line) throws IOException {
			List<String> arguments = List.of(tail(fields, 4, 4, line));
			LanguageServer server = new LanguageServer(fields[1], fields[2], fields[3], arguments);
			List<LanguageServer> alternatives = serversByLanguage.computeIfAbsent(fields[1], key -> new ArrayList<>());
			if (alternatives.stream().anyMatch(other -> other.command().equals(server.command()))) {
				throw new IOException("duplicate server: " + fields[1] + " " + fields[3]);
			}
			if (!alternatives.isEmpty() && !alternatives.get(0).sampleFile().equals(server.sampleFile())) {
				throw new IOException("another sample file for language " + fields[1] + ": " + fields[2]);
			}
			alternatives.add(server);
		}

		private void requireServers(String kind, Map<String, List<String>> languagesByKey) throws IOException {
			for (Map.Entry<String, List<String>> entry : languagesByKey.entrySet()) {
				Optional<String> missing = entry.getValue().stream()
						.filter(language -> !serversByLanguage.containsKey(language)).findFirst();
				if (missing.isPresent()) {
					throw new IOException(
							"no server for language " + missing.get() + " of " + kind + " " + entry.getKey());
				}
			}
		}

		/** The fields from {@code from} on; the row must have at least {@code minimum} fields. */
		private static String[] tail(String[] fields, int from, int minimum, String line) throws IOException {
			if (fields.length < minimum) {
				throw malformed(line);
			}
			return Arrays.copyOfRange(fields, from, fields.length);
		}

		private static IOException malformed(String line) {
			return new IOException("malformed catalog row: " + line);
		}
	}
}
