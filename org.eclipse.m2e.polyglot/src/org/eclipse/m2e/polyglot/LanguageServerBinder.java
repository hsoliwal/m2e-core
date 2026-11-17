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
import java.util.List;
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
 * serves, from a plug-in or from the user, is left alone; so is another plug-in's content type when the language
 * yields to plug-ins. Of several alternative servers, the first whose command is found is registered.
 */
final class LanguageServerBinder {

	static final String PROGRAM_TYPE = "org.eclipse.ui.externaltools.ProgramLaunchConfigurationType";

	static final String ATTR_LOCATION = "org.eclipse.ui.externaltools.ATTR_LOCATION";

	static final String ATTR_ARGUMENTS = "org.eclipse.ui.externaltools.ATTR_TOOL_ARGUMENTS";

	static final String NAME_PREFIX = "Language server: ";

	/** The prefix of the content types this bundle declares. */
	static final String OWN_CONTENT_TYPES = "org.eclipse.m2e.polyglot.";

	/** A server and where its command was found. */
	record Located(LanguageServer server, Path executable) {
	}

	BindOutcome bind(LanguageServer server, Function<String, Optional<Path>> locator) throws CoreException {
		return bind(new LanguageBinding(server.language(), List.of(server), false), locator);
	}

	BindOutcome bind(LanguageBinding binding, Function<String, Optional<Path>> locator) throws CoreException {
		IContentType contentType = Platform.getContentTypeManager().findContentTypeFor(binding.sampleFile());
		if (contentType == null) {
			return BindOutcome.NO_CONTENT_TYPE;
		}
		if (served(contentType)) {
			return BindOutcome.ALREADY_SERVED;
		}
		if (binding.yieldsToPlugins() && !contentType.getId().startsWith(OWN_CONTENT_TYPES)) {
			return BindOutcome.IDE_PLUGIN;
		}
		Optional<Located> located = locate(binding, locator);
		return located.isEmpty() ? BindOutcome.NOT_ON_PATH : register(contentType, located.get());
	}

	/** True when an LSP4E server, from a launch configuration or a plug-in, serves exactly this content type. */
	static boolean served(IContentType contentType) {
		LanguageServersRegistry registry = LanguageServersRegistry.getInstance();
		return Stream.concat(registry.getContentTypeToLSPLaunches().stream().map(entry -> entry.getKey()),
				registry.getContentTypeToLSPExtensions().stream().map(entry -> entry.getKey()))
				.anyMatch(contentType::equals);
	}

	/** The first alternative whose command the locator finds. */
	static Optional<Located> locate(LanguageBinding binding, Function<String, Optional<Path>> locator) {
		return binding.servers().stream()
				.flatMap(server -> locator.apply(server.command()).map(path -> new Located(server, path)).stream())
				.findFirst();
	}

	private static BindOutcome register(IContentType contentType, Located located) throws CoreException {
		ILaunchManager launches = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType type = launches.getLaunchConfigurationType(PROGRAM_TYPE);
		if (type == null) {
			return BindOutcome.NO_PROGRAM_LAUNCH_TYPE;
		}
		Optional<ILaunchConfiguration> existing = findConfiguration(launches, type, located.server());
		ILaunchConfiguration configuration = existing.isPresent() ? existing.get()
				: createConfiguration(type, located.server(), located.executable());
		LanguageServersRegistry.getInstance().registerAssociation(contentType, configuration,
				Set.of(ILaunchManager.RUN_MODE));
		return BindOutcome.BOUND;
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
