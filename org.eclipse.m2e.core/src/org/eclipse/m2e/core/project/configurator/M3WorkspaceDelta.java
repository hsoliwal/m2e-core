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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

/**
 * A read-only snapshot of Eclipse's original resource delta, owned by the
 * Eclipse workspace and independent of Maven's version or compiler runtime.
 * It does not run a builder or infer that Maven goals can be skipped.
 */
public final class M3WorkspaceDelta {
  public static final int MAX_CHANGES = 65_536;

  public enum Kind { ADDED, MODIFIED, DELETED }

  public record Change(String projectRelativePath, Kind kind) {
    public Change {
      projectRelativePath = checkedPath(projectRelativePath);
      kind = Objects.requireNonNull(kind, "kind");
    }
  }

  /**
   * @param projectName Eclipse's original project name
   * @param buildKind Eclipse's original build kind
   * @param deltaComplete whether the captured file delta covers this build kind
   * @param changes immutable, lexicographically ordered file changes
   */
  public record Snapshot(String projectName, int buildKind, boolean deltaComplete, List<Change> changes) {
    public Snapshot {
      projectName = Objects.requireNonNull(projectName, "projectName");
      if(projectName.isBlank()) {
        throw new IllegalArgumentException("empty project name");
      }
      changes = List.copyOf(changes);
    }

    /** A host delta is never permission to skip a Maven goal. */
    public boolean authorizesMavenGoalSkipping() {
      return false;
    }
  }

  private M3WorkspaceDelta() {}

  /**
   * Captures only the owner-supplied delta. For FULL/CLEAN, null or truncated
   * deltas, returned coverage is false: the caller must reconcile normally.
   */
  public static Snapshot capture(IProject project, int buildKind, IResourceDelta delta, IProgressMonitor monitor)
      throws CoreException {
    Objects.requireNonNull(project, "project");
    String name = project.getName();
    if(buildKind == IncrementalProjectBuilder.FULL_BUILD
        || buildKind == IncrementalProjectBuilder.CLEAN_BUILD || delta == null) {
      return new Snapshot(name, buildKind, false, List.of());
    }
    if(buildKind != IncrementalProjectBuilder.INCREMENTAL_BUILD
        && buildKind != IncrementalProjectBuilder.AUTO_BUILD) {
      return new Snapshot(name, buildKind, false, List.of());
    }

    Map<String, Change> changed = new TreeMap<>();
    boolean[] complete = {true};
    delta.accept(node -> {
      if(monitor != null && monitor.isCanceled()) {
        throw new OperationCanceledException();
      }
      IResource resource = node.getResource();
      if(resource == null) {
        complete[0] = false;
        return true;
      }
      if(resource.getType() != IResource.FILE) {
        return true;
      }
      Kind kind = switch(node.getKind()) {
        case IResourceDelta.ADDED -> Kind.ADDED;
        case IResourceDelta.CHANGED -> Kind.MODIFIED;
        case IResourceDelta.REMOVED -> Kind.DELETED;
        default -> null;
      };
      if(kind == null) {
        complete[0] = false;
        return true;
      }
      // Marker-only workspace updates are not a source content modification.
      if(kind == Kind.MODIFIED && node.getFlags() == IResourceDelta.MARKERS) {
        return true;
      }
      if(resource.isLinked(IResource.CHECK_ANCESTORS) || resource.isDerived(IResource.CHECK_ANCESTORS)) {
        complete[0] = false;
      }
      String path = checkedPath(resource.getProjectRelativePath().toPortableString());
      if(changed.size() == MAX_CHANGES && !changed.containsKey(path)) {
        complete[0] = false;
        return false;
      }
      Change previous = changed.putIfAbsent(path, new Change(path, kind));
      if(previous != null) {
        // Inconsistent or duplicated callbacks cannot prove complete coverage.
        complete[0] = false;
      }
      return true;
    });
    List<Change> snapshot = new ArrayList<>(Math.min(changed.size(), MAX_CHANGES));
    int index = 0;
    for(Change change : changed.values()) {
      if(index++ == MAX_CHANGES) {
        break;
      }
      snapshot.add(change);
    }
    return new Snapshot(name, buildKind, complete[0], snapshot);
  }

  private static String checkedPath(String path) {
    Objects.requireNonNull(path, "projectRelativePath");
    if(path.isEmpty() || path.length() > 2048 || path.startsWith("/") || path.indexOf('\\') >= 0
        || path.indexOf(':') >= 0 || path.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("invalid project-relative delta path");
    }
    for(String segment : path.split("/", -1)) {
      if(segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
        throw new IllegalArgumentException("noncanonical delta path segment");
      }
    }
    for(int i = 0; i < path.length(); i++) {
      char c = path.charAt(i);
      if(Character.isHighSurrogate(c)) {
        if(++i >= path.length() || !Character.isLowSurrogate(path.charAt(i))) {
          throw new IllegalArgumentException("malformed delta Unicode");
        }
      } else if(Character.isLowSurrogate(c)) {
        throw new IllegalArgumentException("malformed delta Unicode");
      }
    }
    return path;
  }
}
