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

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Finds a command the way the operating system starts it: on {@code PATH}, and on Windows with the {@code PATHEXT}
 * extensions, since Windows starts only files with an executable extension.
 */
final class Executables {

	private static final String DEFAULT_PATHEXT = ".COM;.EXE;.BAT;.CMD";

	private Executables() {
	}

	static Optional<Path> find(String command) {
		String path = System.getenv("PATH");
		String pathExt = System.getenv("PATHEXT");
		boolean windows = System.getProperty("os.name", "").startsWith("Windows");
		return find(command, path == null ? "" : path, pathExt == null ? DEFAULT_PATHEXT : pathExt, windows);
	}

	static Optional<Path> find(String command, String path, String pathExt, boolean windows) {
		if (command.isBlank()) {
			return Optional.empty();
		}
		if (command.indexOf('/') >= 0 || command.indexOf('\\') >= 0) {
			return Files.isRegularFile(Path.of(command)) ? Optional.of(Path.of(command)) : Optional.empty();
		}
		List<String> names = candidates(command, pathExt, windows);
		for (String folder : path.split(windows ? ";" : ":")) {
			Optional<Path> found = findIn(folder, names, windows);
			if (found.isPresent()) {
				return found;
			}
		}
		return Optional.empty();
	}

	private static List<String> candidates(String command, String pathExt, boolean windows) {
		if (!windows) {
			return List.of(command);
		}
		List<String> extensions = new ArrayList<>();
		for (String extension : pathExt.split(";")) {
			if (!extension.isBlank()) {
				extensions.add(extension.toLowerCase(Locale.ROOT));
			}
		}
		List<String> names = new ArrayList<>();
		String lower = command.toLowerCase(Locale.ROOT);
		if (extensions.stream().anyMatch(lower::endsWith)) {
			names.add(command);
		}
		extensions.forEach(extension -> names.add(command + extension));
		return names;
	}

	private static Optional<Path> findIn(String folder, List<String> names, boolean windows) {
		if (folder.isBlank()) {
			return Optional.empty();
		}
		for (String name : names) {
			try {
				Path file = Path.of(folder, name);
				if (Files.isRegularFile(file) && (windows || Files.isExecutable(file))) {
					return Optional.of(file);
				}
			} catch (InvalidPathException e) {
				return Optional.empty();
			}
		}
		return Optional.empty();
	}
}
