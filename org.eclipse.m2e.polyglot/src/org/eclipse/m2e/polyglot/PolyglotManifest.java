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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * The tools a polyglot task manifest (format {@value #MAGIC}) runs: the third field of every
 * {@code task} row. Other rows (arguments, prerequisites, inputs, outputs) do not name tools and are ignored.
 */
final class PolyglotManifest {

	static final String MAGIC = "M3-POLYGLOT-TASKS-V1";

	private static final String[] WINDOWS_EXTENSIONS = { ".exe", ".cmd", ".bat", ".com" };

	/** The first field of a tool provider task: {@code @m3:<provider>:<capability>:<language>}. */
	private static final String M3_PROVIDER = "@m3";

	private PolyglotManifest() {
	}

	/** The tools of the manifest file; empty when the file does not exist. */
	static Set<String> tools(Path manifest) throws IOException {
		if (!Files.exists(manifest)) {
			return Set.of();
		}
		return tools(Files.readAllLines(manifest, StandardCharsets.UTF_8));
	}

	/** The normalised tools of the task rows, sorted; the first line must be {@value #MAGIC}. */
	static Set<String> tools(List<String> lines) throws IOException {
		if (lines.isEmpty() || !MAGIC.equals(stripCarriageReturn(lines.get(0)))) {
			throw new IOException("not a polyglot task manifest");
		}
		Set<String> tools = new TreeSet<>();
		for (String raw : lines.subList(1, lines.size())) {
			String line = stripCarriageReturn(raw);
			if (line.isBlank() || line.startsWith("#")) {
				continue;
			}
			String[] fields = line.split("\t", -1);
			if ("task".equals(fields[0])) {
				if (fields.length != 4 || fields[2].isBlank()) {
					throw new IOException("malformed task row: " + line);
				}
				tools.add(toolName(fields[2]));
			}
		}
		return Collections.unmodifiableSet(tools);
	}

	/**
	 * The last path segment, lower case, without one trailing Windows executable extension; for a tool provider task
	 * ({@code @m3:cargo:interpreter:rust}) the provider ({@code cargo}).
	 */
	static String toolName(String tool) {
		String[] provider = tool.split(":", -1);
		if (provider.length == 4 && M3_PROVIDER.equals(provider[0]) && !provider[1].isEmpty()) {
			// @m3:<provider>:<capability>:<language>: the provider names the tool (cargo for Rust tests).
			return provider[1].toLowerCase(Locale.ROOT);
		}
		String name = tool.substring(Math.max(tool.lastIndexOf('/'), tool.lastIndexOf('\\')) + 1)
				.toLowerCase(Locale.ROOT);
		for (String extension : WINDOWS_EXTENSIONS) {
			if (name.endsWith(extension)) {
				return name.substring(0, name.length() - extension.length());
			}
		}
		return name;
	}

	private static String stripCarriageReturn(String line) {
		return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
	}
}
