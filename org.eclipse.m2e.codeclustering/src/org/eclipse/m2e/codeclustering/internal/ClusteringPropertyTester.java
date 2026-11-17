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

import org.eclipse.core.expressions.PropertyTester;


/**
 * Property {@code org.eclipse.m2e.codeclustering.enabled}: whether the code-clustering server is switched on in the
 * preferences. The language server mappings test it before they test the Maven nature.
 */
public class ClusteringPropertyTester extends PropertyTester {

  @Override
  public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
    return ServerOptions.ENABLED.equals(property) && ClusteringPreferences.isEnabled();
  }
}
