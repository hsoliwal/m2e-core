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

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;


/**
 * Default preferences: the server is off, version {@link ServerArtifacts#DEFAULT_VERSION}, the server's default
 * {@code minTokens}, the built-in front ends, the Java that runs Eclipse, a resolved class path, and no clang front
 * end.
 */
public class ClusteringPreferenceInitializer extends AbstractPreferenceInitializer {

  @Override
  public void initializeDefaultPreferences() {
    IEclipsePreferences defaults = DefaultScope.INSTANCE.getNode(ClusteringPreferences.PLUGIN_ID);
    defaults.putBoolean(ServerOptions.ENABLED, false);
    defaults.put(ServerOptions.VERSION, ServerArtifacts.DEFAULT_VERSION);
    defaults.putInt(ServerOptions.MIN_TOKENS, ServerOptions.DEFAULT_MIN_TOKENS);
    defaults.put(ServerOptions.PROFILES, ""); //$NON-NLS-1$
    defaults.put(ServerOptions.SERVERS, ""); //$NON-NLS-1$
    defaults.put(ServerOptions.JAVA, ""); //$NON-NLS-1$
    defaults.put(ServerOptions.VM_ARGUMENTS, ""); //$NON-NLS-1$
    defaults.put(ServerOptions.CLASSPATH, ""); //$NON-NLS-1$
    defaults.putBoolean(ServerOptions.CLANG, false);
    defaults.put(ServerOptions.CLANG_EXECUTABLE, ""); //$NON-NLS-1$
  }
}
