/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.m2e.codeclustering.internal;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.lsp4e.client.DefaultLanguageClient;
import org.eclipse.lsp4j.WorkspaceFolder;

import org.eclipse.m2e.core.MavenPlugin;


/**
 * Announces only the open Maven projects as workspace folders, so the server scans Maven projects and nothing else.
 * Modules inside the folder of another announced project are left out, see {@link WorkspaceRoots}.
 */
public class MavenProjectsLanguageClient extends DefaultLanguageClient {

  @Override
  public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
    return CompletableFuture.completedFuture(mavenProjectFolders());
  }

  static List<WorkspaceFolder> mavenProjectFolders() {
    List<IProject> projects = Arrays.stream(ResourcesPlugin.getWorkspace().getRoot().getProjects())
        .filter(MavenPlugin::isMavenProject).filter(p -> p.getLocation() != null).toList();
    return WorkspaceRoots.outermost(projects, p -> Path.of(p.getLocation().toOSString())).stream()
        .map(p -> new WorkspaceFolder(p.getLocationURI().toASCIIString(), p.getName())).toList();
  }
}
