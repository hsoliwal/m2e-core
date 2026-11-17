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

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;


/**
 * Preferences > Maven > Code Clustering.
 */
public final class ClusteringPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

  public ClusteringPreferencePage() {
    super(GRID);
    setPreferenceStore(new ScopedPreferenceStore(InstanceScope.INSTANCE, ClusteringPreferences.PLUGIN_ID));
    setDescription(Messages.ClusteringPreferencePage_description);
  }

  @Override
  public void init(IWorkbench workbench) {
  }

  @Override
  protected void createFieldEditors() {
    Composite parent = getFieldEditorParent();
    addField(new BooleanFieldEditor(ServerOptions.ENABLED, Messages.ClusteringPreferencePage_enabled, parent));
    addField(new VersionFieldEditor(parent));
    IntegerFieldEditor minTokens = new IntegerFieldEditor(ServerOptions.MIN_TOKENS,
        Messages.ClusteringPreferencePage_minTokens, parent);
    minTokens.setValidRange(1, Integer.MAX_VALUE);
    addField(minTokens);
    addField(new StringFieldEditor(ServerOptions.PROFILES, Messages.ClusteringPreferencePage_profiles, parent));
    addField(new StringFieldEditor(ServerOptions.SERVERS, Messages.ClusteringPreferencePage_servers, parent));
    addField(new StringFieldEditor(ServerOptions.JAVA, Messages.ClusteringPreferencePage_java, parent));
    addField(new StringFieldEditor(ServerOptions.VM_ARGUMENTS, Messages.ClusteringPreferencePage_vmArguments, parent));
    addField(new StringFieldEditor(ServerOptions.CLASSPATH, Messages.ClusteringPreferencePage_classpath, parent));
  }

  /**
   * The version field accepts what {@link ServerArtifacts#version(String)} accepts.
   */
  private static final class VersionFieldEditor extends StringFieldEditor {

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
}
