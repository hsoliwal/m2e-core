/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Synexia Project.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.m2e.core.project.configurator;

import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.m2e.core.project.IMavenProjectFacade;

/**
 * Opt-in m2e participant. It records the original Eclipse delta in the
 * facade's transient session context; it does not execute or suppress Mojos.
 * Must be explicitly installed by an m2e lifecycle mapping provider.
 */
public final class M3WorkspaceDeltaBuildParticipant extends AbstractBuildParticipant {
  public static final String PROPERTY = "org.eclipse.m2e.core.m3.workspaceDelta";

  /** Original MavenBuilderImpl opt-in switch; off for existing workspaces. */
  public static final String ENABLE_PROPERTY = "m3.ide.delta.capture";

  @Override
  public Set<IProject> build(int kind, IProgressMonitor monitor) throws Exception {
    IMavenProjectFacade facade = getMavenProjectFacade();
    if(facade == null) {
      return Set.of();
    }
    // Invalidate the preceding generation before any operation that can fail.
    // Otherwise a failed/cancelled Eclipse build leaves stale advice visible.
    facade.setSessionProperty(PROPERTY, null);
    IProject project = facade.getProject();
    IResourceDelta delta = kind == IncrementalProjectBuilder.INCREMENTAL_BUILD
        || kind == IncrementalProjectBuilder.AUTO_BUILD ? getDelta(project) : null;
    M3WorkspaceDelta.Snapshot snapshot = M3WorkspaceDelta.capture(project, kind, delta, monitor);
    facade.setSessionProperty(PROPERTY, snapshot);
    return Set.of();
  }

  @Override
  public boolean callOnEmptyDelta() {
    return true;
  }

  /** Registration/clean invalidates transient advice without touching outputs. */
  @Override
  public void clean(IProgressMonitor monitor) {
    IMavenProjectFacade facade = getMavenProjectFacade();
    if(facade != null) {
      facade.setSessionProperty(PROPERTY, null);
    }
  }
}
