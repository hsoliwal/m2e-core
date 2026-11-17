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
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.lsp4e.server.ProcessStreamConnectionProvider;


/**
 * Starts the code-clustering language server ({@code ClusteringServerMain}) on standard input and output. The class
 * path is the explicit one from the preferences, else the server artifacts and their runtime dependencies resolved
 * through m2e. The server runs on the Java that runs Eclipse unless the preferences name another one.
 */
public class ClusteringConnectionProvider extends ProcessStreamConnectionProvider {

  private static final Logger log = LoggerFactory.getLogger(ClusteringConnectionProvider.class);

  private static final ServerClasspathResolver RESOLVER = new ServerClasspathResolver();

  private volatile int minTokens = ServerOptions.DEFAULT_MIN_TOKENS;

  @Override
  public void start() throws IOException {
    ServerOptions options = ClusteringPreferences.load();
    String javaHome = System.getProperty("java.home"); //$NON-NLS-1$
    if(options.javaExecutable().isEmpty() && !ServerCommandLine.runningJavaHasCompiler()) {
      log.warn("{} has no jdk.compiler module: the code-clustering server cannot scan Java files."
          + " Set a JDK in Preferences > Maven > Code Clustering.", javaHome);
    }
    minTokens = options.minTokens();
    String java = ServerCommandLine.javaExecutable(options.javaExecutable(), javaHome,
        Platform.OS_WIN32.equals(Platform.getOS()));
    setCommands(ServerCommandLine.build(options, java, classpath(options)));
    IPath root = ResourcesPlugin.getWorkspace().getRoot().getLocation();
    if(root != null) {
      setWorkingDirectory(root.toOSString());
    }
    super.start();
  }

  @Override
  public Object getInitializationOptions(URI rootUri) {
    return Map.of(ServerOptions.MIN_TOKENS, minTokens);
  }

  private static List<File> classpath(ServerOptions options) throws IOException {
    if(!options.classpath().isEmpty()) {
      return options.classpath().stream().map(File::new).toList();
    }
    try {
      return RESOLVER.resolve(ServerArtifacts.coordinates(options.version(), options.clang()),
          new NullProgressMonitor());
    } catch(CoreException | IllegalArgumentException ex) {
      String message = "The code-clustering language server is not started: " + ex.getMessage() //$NON-NLS-1$
          + ". Install " + ServerArtifacts.SERVER_ARTIFACT_ID + " and " + ServerArtifacts.PLUGIN_ARTIFACT_ID //$NON-NLS-1$ //$NON-NLS-2$
          + " into the local repository (mvn install), or set the server class path" //$NON-NLS-1$
          + " in Preferences > Maven > Code Clustering."; //$NON-NLS-1$
      log.error(message, ex);
      throw new IOException(message, ex);
    }
  }
}
