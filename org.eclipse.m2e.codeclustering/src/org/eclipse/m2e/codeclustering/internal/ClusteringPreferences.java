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

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IPreferencesService;


/**
 * Reads the code-clustering preferences (instance scope, then the defaults of
 * {@link ClusteringPreferenceInitializer}).
 */
public final class ClusteringPreferences {

  public static final String PLUGIN_ID = "org.eclipse.m2e.codeclustering"; //$NON-NLS-1$

  private ClusteringPreferences() {
  }

  /** Whether the user switched the server on; it is off by default. */
  public static boolean isEnabled() {
    return Platform.getPreferencesService().getBoolean(PLUGIN_ID, ServerOptions.ENABLED, false, null);
  }

  public static ServerOptions load() {
    IPreferencesService service = Platform.getPreferencesService();
    return ServerOptions.read(key -> service.getString(PLUGIN_ID, key, "", null)); //$NON-NLS-1$
  }
}
