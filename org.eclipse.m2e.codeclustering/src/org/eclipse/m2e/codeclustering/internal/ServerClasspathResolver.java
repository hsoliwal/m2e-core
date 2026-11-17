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

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;

import org.apache.maven.RepositoryUtils;

import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.embedder.IMavenExecutionContext;


/**
 * Resolves the server artifacts and their runtime dependencies with m2e's Maven settings. The local repository is
 * tried first, offline; when that is not enough and Maven is not offline, the repositories of settings.xml follow
 * (mirrors, proxies and authentication applied). Results are cached per coordinates and local repository while all
 * their files exist.
 */
final class ServerClasspathResolver {

  private static final Logger log = LoggerFactory.getLogger(ServerClasspathResolver.class);

  private final Map<String, List<File>> cache = new ConcurrentHashMap<>();

  List<File> resolve(List<String> coordinates, IProgressMonitor monitor) throws CoreException {
    IMaven maven = MavenPlugin.getMaven();
    String key = String.join(",", coordinates) + '@' + maven.getLocalRepositoryPath(); //$NON-NLS-1$
    List<File> cached = cache.get(key);
    if(cached != null && cached.stream().allMatch(File::isFile)) {
      return cached;
    }
    List<File> files = maven.createExecutionContext()
        .execute((context, m) -> resolve(maven, context, coordinates), monitor);
    cache.put(key, files);
    return files;
  }

  private static List<File> resolve(IMaven maven, IMavenExecutionContext context, List<String> coordinates)
      throws CoreException {
    RepositorySystem system = maven.lookup(RepositorySystem.class);
    DependencyRequest request = request(coordinates, RepositoryUtils.toRepos(maven.getArtifactRepositories()));
    RepositorySystemSession session = context.getRepositorySession();
    try {
      return files(system.resolveDependencies(new DefaultRepositorySystemSession(session).setOffline(true), request));
    } catch(DependencyResolutionException notLocal) {
      if(session.isOffline()) {
        throw failure(coordinates, notLocal);
      }
      log.info("{} not complete in the local repository {}, resolving from the remote repositories", coordinates,
          maven.getLocalRepositoryPath());
      try {
        return files(system.resolveDependencies(session, request));
      } catch(DependencyResolutionException notRemote) {
        notRemote.addSuppressed(notLocal);
        throw failure(coordinates, notRemote);
      }
    }
  }

  /**
   * Returns a request for the coordinates (runtime scope) and their compile and runtime dependencies.
   */
  static DependencyRequest request(List<String> coordinates, List<RemoteRepository> repositories) {
    CollectRequest collect = new CollectRequest();
    collect.setRequestContext("runtime"); //$NON-NLS-1$
    collect.setRepositories(repositories);
    coordinates.forEach(c -> collect.addDependency(new Dependency(new DefaultArtifact(c), JavaScopes.RUNTIME)));
    return new DependencyRequest(collect, DependencyFilterUtils.classpathFilter(JavaScopes.RUNTIME));
  }

  private static List<File> files(DependencyResult result) {
    return result.getArtifactResults().stream().map(r -> r.getArtifact().getFile()).toList();
  }

  private static CoreException failure(List<String> coordinates, Exception cause) {
    return new CoreException(Status.error("Cannot resolve " + coordinates + ": " + cause.getMessage(), cause)); //$NON-NLS-1$ //$NON-NLS-2$
  }
}
