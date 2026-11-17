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

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import org.apache.maven.plugin.MojoExecution;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.configurator.AbstractProjectConfigurator;
import org.eclipse.m2e.core.project.configurator.MojoExecutionKey;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gives a Maven project that runs native tools from a polyglot task manifest a language server for every language
 * those tools build. A Maven plugin opts in by mapping its executions to this configurator in the m2e lifecycle
 * mapping metadata embedded in its jar ({@code META-INF/m2e/lifecycle-mapping-metadata.xml}); each such execution's
 * {@code manifest} parameter names the task manifest. Languages and servers come from {@link LanguageServerCatalog}.
 */
public class PolyglotProjectConfigurator extends AbstractProjectConfigurator {

	static final String MANIFEST_PARAMETER = "manifest";

	private static final Logger LOG = LoggerFactory.getLogger(PolyglotProjectConfigurator.class);

	@Override
	public void configure(ProjectConfigurationRequest request, IProgressMonitor monitor) throws CoreException {
		IMavenProjectFacade facade = request.mavenProjectFacade();
		Set<String> tools = new TreeSet<>();
		for (MojoExecutionKey key : getConfiguratorExecutions(facade).getOrDefault(getId(), Set.of())) {
			tools.addAll(tools(facade, facade.getMojoExecution(key, monitor), monitor));
		}
		if (tools.isEmpty()) {
			return;
		}
		String project = facade.getProject().getName();
		LanguageServerBinder binder = new LanguageServerBinder();
		for (LanguageServer server : catalog().serversFor(tools)) {
			BindOutcome outcome = binder.bind(server, Executables::find);
			LOG.info("{}: {} language server {} -> {}", project, server.language(), server.command(), outcome);
		}
	}

	private static Set<String> tools(IMavenProjectFacade facade, MojoExecution execution, IProgressMonitor monitor)
			throws CoreException {
		File manifest = execution == null ? null
				: facade.getMojoParameterValue(execution, MANIFEST_PARAMETER, File.class, monitor);
		if (manifest == null) {
			return Set.of();
		}
		try {
			return PolyglotManifest.tools(manifest.toPath());
		} catch (IOException e) {
			LOG.warn("{}: skipping polyglot task manifest {}: {}", facade.getProject().getName(), manifest,
					e.getMessage());
			return Set.of();
		}
	}

	private static LanguageServerCatalog catalog() throws CoreException {
		try {
			return LanguageServerCatalog.load();
		} catch (IOException e) {
			throw new CoreException(Status.error("Cannot read the language server catalog", e));
		}
	}
}
