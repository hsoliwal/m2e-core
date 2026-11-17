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

import java.util.List;

/**
 * A language server for one language: a file name whose Eclipse content type is that language's, and the command
 * line that starts the server over standard input and output.
 */
record LanguageServer(String language, String sampleFile, String command, List<String> arguments) {

	LanguageServer {
		require(language, "language");
		require(sampleFile, "sampleFile");
		require(command, "command");
		arguments = List.copyOf(arguments);
	}

	private static void require(String value, String component) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(component + " must not be blank");
		}
	}
}
