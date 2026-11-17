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

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.lsp4e.LanguageServersRegistry;

/**
 * Registers a language server with LSP4E the way LSP4E's <i>Language Servers</i> preference page does: an External
 * Tools program launch configuration associated with a content type. A content type that any LSP4E server already
 * serves, from a plug-in or from the user, is left alone.
 */
final class LanguageServerBinder {

	static final String PROGRAM_TYPE = "org.eclipse.ui.externaltools.ProgramLaunchConfigurationType";

	static final String ATTR_LOCATION = "org.eclipse.ui.externaltools.ATTR_LOCATION";

	static final String ATTR_ARGUMENTS = "org.eclipse.ui.externaltools.ATTR_TOOL_ARGUMENTS";

	static final String NAME_PREFIX = "Language server: ";

	BindOutcome bind(LanguageServer server, Function<String, Optional<Path>> locator) throws CoreException {
		IContentType contentType = Platform.getContentTypeManager().findContentTypeFor(server.sampleFile());
		if (contentType == null) {
			return BindOutcome.NO_CONTENT_TYPE;
		}
		if (served(contentType)) {
			return BindOutcome.ALREADY_SERVED;
		}
		Optional<Path> executable = locator.apply(server.command());
		if (executable.isEmpty()) {
			return BindOutcome.NOT_ON_PATH;
		}
		ILaunchManager launches = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType type = launches.getLaunchConfigurationType(PROGRAM_TYPE);
		if (type == null) {
			return BindOutcome.NO_PROGRAM_LAUNCH_TYPE;
		}
		Optional<ILaunchConfiguration> existing = findConfiguration(launches, type, server);
		ILaunchConfiguration configuration = existing.isPresent() ? existing.get()
				: createConfiguration(type, server, executable.get());
		LanguageServersRegistry.getInstance().registerAssociation(contentType, configuration,
				Set.of(ILaunchManager.RUN_MODE));
		return BindOutcome.BOUND;
	}

	/** True when an LSP4E server, from a launch configuration or a plug-in, serves exactly this content type. */
	static boolean served(IContentType contentType) {
		LanguageServersRegistry registry = LanguageServersRegistry.getInstance();
		return Stream.concat(registry.getContentTypeToLSPLaunches().stream().map(entry -> entry.getKey()),
				registry.getContentTypeToLSPExtensions().stream().map(entry -> entry.getKey()))
				.anyMatch(contentType::equals);
	}

	private static Optional<ILaunchConfiguration> findConfiguration(ILaunchManager launches,
			ILaunchConfigurationType type, LanguageServer server) throws CoreException {
		String name = NAME_PREFIX + server.command();
		return Stream.of(launches.getLaunchConfigurations(type)).filter(c -> name.equals(c.getName())).findFirst();
	}

	private static ILaunchConfiguration createConfiguration(ILaunchConfigurationType type, LanguageServer server,
			Path executable) throws CoreException {
		ILaunchConfigurationWorkingCopy copy = type.newInstance(null, NAME_PREFIX + server.command());
		copy.setAttribute(ATTR_LOCATION, executable.toAbsolutePath().toString());
		copy.setAttribute(ATTR_ARGUMENTS, String.join(" ", server.arguments()));
		return copy.doSave();
	}
}
