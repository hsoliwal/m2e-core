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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;


/**
 * Selects the workspace folders the server scans. A Maven module that lives inside its parent's folder is already
 * scanned with the parent; announcing both would scan its files twice and report every atom as a duplicate of itself.
 */
public final class WorkspaceRoots {

  private WorkspaceRoots() {
  }

  /**
   * Returns the items whose location lies inside no other item's location, in their original order. Locations are
   * compared by path elements, so {@code /a/bc} is not inside {@code /a/b}; of two items with the same location, the
   * first is kept.
   */
  public static <T> List<T> outermost(List<T> items, Function<T, Path> location) {
    List<Path> paths = items.stream().map(item -> location.apply(item).toAbsolutePath().normalize()).toList();
    List<Integer> shortestFirst = IntStream.range(0, items.size()).boxed()
        .sorted(Comparator.comparingInt(i -> paths.get(i).getNameCount())).toList();
    boolean[] keep = new boolean[items.size()];
    List<Path> kept = new ArrayList<>();
    for(int i : shortestFirst) {
      Path path = paths.get(i);
      if(kept.stream().noneMatch(path::startsWith)) {
        kept.add(path);
        keep[i] = true;
      }
    }
    return IntStream.range(0, items.size()).filter(i -> keep[i]).mapToObj(items::get).toList();
  }
}
