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

import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.widgets.Composite;

/**
 * The version field accepts what {@link ServerArtifacts#version(String)} accepts.
 */
final class VersionFieldEditor extends StringFieldEditor {

  VersionFieldEditor(Composite parent) {
    super(ServerOptions.VERSION, Messages.ClusteringPreferencePage_version, parent);
  }

  @Override
  protected boolean doCheckState() {
    try {
      ServerArtifacts.version(getStringValue());
      return true;
    } catch(IllegalArgumentException ex) {
      setErrorMessage(ex.getMessage());
      return false;
    }
  }
}
