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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Which languages a tool builds and which language server serves each language, read from
 * {@value #RESOURCE} (format {@value #MAGIC}): {@code tool <tool> <language>...} and
 * {@code server <language> <sample file> <command> <argument>...} rows.
 */
final class LanguageServerCatalog {

	static final String MAGIC = "M2E-POLYGLOT-LANGUAGE-SERVERS-V1";

	static final String RESOURCE = "/language-servers.tsv";

	private final Map<String, List<String>> languagesByTool;

	private final Map<String, LanguageServer> serversByLanguage;

	private LanguageServerCatalog(Map<String, List<String>> languagesByTool,
			Map<String, LanguageServer> serversByLanguage) {
		this.languagesByTool = Collections.unmodifiableMap(new LinkedHashMap<>(languagesByTool));
		this.serversByLanguage = Collections.unmodifiableMap(new LinkedHashMap<>(serversByLanguage));
	}

	static LanguageServerCatalog parse(List<String> lines) throws IOException {
		if (lines.isEmpty() || !MAGIC.equals(stripCarriageReturn(lines.get(0)))) {
			throw new IOException("not a language server catalog");
		}
		Map<String, List<String>> languagesByTool = new LinkedHashMap<>();
		Map<String, LanguageServer> serversByLanguage = new LinkedHashMap<>();
		for (String raw : lines.subList(1, lines.size())) {
			String line = stripCarriageReturn(raw);
			if (!line.isBlank() && !line.startsWith("#")) {
				read(line, languagesByTool, serversByLanguage);
			}
		}
		requireServers(languagesByTool, serversByLanguage);
		return new LanguageServerCatalog(languagesByTool, serversByLanguage);
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

	/** The servers of the tools' languages, each once: tools in natural order, languages in catalog order. */
	List<LanguageServer> serversFor(Collection<String> tools) {
		Map<String, LanguageServer> servers = new LinkedHashMap<>();
		for (String tool : new TreeSet<>(tools)) {
			for (String language : languagesByTool.getOrDefault(tool, List.of())) {
				servers.putIfAbsent(language, serversByLanguage.get(language));
			}
		}
		return List.copyOf(servers.values());
	}

	private static void read(String line, Map<String, List<String>> languagesByTool,
			Map<String, LanguageServer> serversByLanguage) throws IOException {
		String[] fields = line.split("\t", -1);
		if ("tool".equals(fields[0]) && fields.length >= 3) {
			List<String> languages = List.of(Arrays.copyOfRange(fields, 2, fields.length));
			if (languagesByTool.putIfAbsent(fields[1], languages) != null) {
				throw new IOException("duplicate tool: " + fields[1]);
			}
		} else if ("server".equals(fields[0]) && fields.length >= 4) {
			List<String> arguments = List.of(Arrays.copyOfRange(fields, 4, fields.length));
			LanguageServer server = new LanguageServer(fields[1], fields[2], fields[3], arguments);
			if (serversByLanguage.putIfAbsent(fields[1], server) != null) {
				throw new IOException("duplicate server: " + fields[1]);
			}
		} else {
			throw new IOException("malformed catalog row: " + line);
		}
	}

	private static void requireServers(Map<String, List<String>> languagesByTool,
			Map<String, LanguageServer> serversByLanguage) throws IOException {
		for (Map.Entry<String, List<String>> tool : languagesByTool.entrySet()) {
			for (String language : tool.getValue()) {
				if (!serversByLanguage.containsKey(language)) {
					throw new IOException("no server for language " + language + " of tool " + tool.getKey());
				}
			}
		}
	}

	private static String stripCarriageReturn(String line) {
		return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
	}
}
