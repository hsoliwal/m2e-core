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
 * The language servers that can serve one language, in order of preference: the first whose command is found is
 * started. A language that yields to plug-ins is bound only to this bundle's own content type, so an installed IDE
 * plug-in that claims its files (Groovy-Eclipse, Kotlin for Eclipse, PDT, Erlide) keeps them.
 */
record LanguageBinding(String language, List<LanguageServer> servers, boolean yieldsToPlugins) {

	LanguageBinding {
		if (servers == null || servers.isEmpty()) {
			throw new IllegalArgumentException("no server for language " + language);
		}
		servers = List.copyOf(servers);
	}

	/** A file name whose Eclipse content type is the language's; every alternative shares it. */
	String sampleFile() {
		return servers.get(0).sampleFile();
	}
}
