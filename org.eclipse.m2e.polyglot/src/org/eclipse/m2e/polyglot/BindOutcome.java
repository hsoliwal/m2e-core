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

/** What binding one language server to its content type did. */
enum BindOutcome {
	/** A launch configuration was registered with LSP4E for the content type. */
	BOUND,
	/** An LSP4E server already serves the content type. */
	ALREADY_SERVED,
	/**
	 * The language yields to plug-ins and another plug-in's content type claims the sample file, or the language's native
	 * IDE editor (WTP's XML editor for XML) is installed.
	 */
	IDE_PLUGIN,
	/** Eclipse knows no content type for the sample file. */
	NO_CONTENT_TYPE,
	/** The server command was not found. */
	NOT_ON_PATH,
	/** The External Tools program launch type is not installed. */
	NO_PROGRAM_LAUNCH_TYPE
}
