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
import java.util.List;
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
 * Gives a Maven project a language server for every language its build compiles. Two kinds of executions are mapped
 * to it, in m2e lifecycle mapping metadata:
 * <ul>
 * <li>executions of a plug-in that runs native tools from a polyglot task manifest, mapped to
 * {@code org.eclipse.m2e.polyglot.configurator} in the metadata embedded in that plug-in's jar
 * ({@code META-INF/m2e/lifecycle-mapping-metadata.xml}); each execution's {@code manifest} parameter names the task
 * manifest, and its tools give the languages;</li>
 * <li>executions of a language's Maven plug-in (GMavenPlus, the Kotlin plug-in), mapped to
 * {@code org.eclipse.m2e.polyglot.languagePlugins} in this bundle's metadata; the catalog's {@code plugin} rows give
 * the languages.</li>
 * </ul>
 * Languages and servers come from {@link LanguageServerCatalog}. Maven keeps building; nothing runs in the Eclipse
 * build.
 */
public class PolyglotProjectConfigurator extends AbstractProjectConfigurator {

	static final String MANIFEST_PARAMETER = "manifest";

	private static final Logger LOG = LoggerFactory.getLogger(PolyglotProjectConfigurator.class);

	@Override
	public void configure(ProjectConfigurationRequest request, IProgressMonitor monitor) throws CoreException {
		IMavenProjectFacade facade = request.mavenProjectFacade();
		LanguageServerCatalog catalog = catalog();
		Set<String> tools = new TreeSet<>();
		Set<String> plugins = new TreeSet<>();
		for (MojoExecutionKey key : getConfiguratorExecutions(facade).getOrDefault(getId(), Set.of())) {
			String plugin = pluginKey(key);
			if (catalog.knowsPlugin(plugin)) {
				plugins.add(plugin);
			} else {
				tools.addAll(tools(facade, facade.getMojoExecution(key, monitor), monitor));
			}
		}
		bind(facade.getProject().getName(), catalog.bindingsFor(tools, plugins));
	}

	/** The plug-in of an execution as the catalog's {@code plugin} rows name it: {@code groupId:artifactId}. */
	static String pluginKey(MojoExecutionKey key) {
		return key.groupId() + ":" + key.artifactId();
	}

	private static void bind(String project, List<LanguageBinding> bindings) throws CoreException {
		LanguageServerBinder binder = new LanguageServerBinder();
		for (LanguageBinding binding : bindings) {
			BindOutcome outcome = binder.bind(binding, Executables::find);
			List<String> commands = binding.servers().stream().map(LanguageServer::command).toList();
			LOG.info("{}: {} language server {} -> {}", project, binding.language(), commands, outcome);
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
