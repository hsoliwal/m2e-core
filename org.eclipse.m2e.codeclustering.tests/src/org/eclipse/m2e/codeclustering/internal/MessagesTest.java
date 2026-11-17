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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.Test;


public class MessagesTest {

  private static final String PAGE = "ClusteringPreferencePage_";

  @Test
  public void testPreferencePageMnemonicsAreUnique() {
    List<Character> mnemonics = Arrays.stream(Messages.class.getFields())
        .filter(field -> field.getName().startsWith(PAGE) && !field.getName().equals(PAGE + "description"))
        .map(MessagesTest::label).map(MessagesTest::mnemonic).toList();
    assertEquals("labels of the preference page", 10, mnemonics.size());
    assertEquals(mnemonics.toString(), mnemonics.size(), Set.copyOf(mnemonics).size());
  }

  private static String label(Field field) {
    try {
      return (String) field.get(null);
    } catch(IllegalAccessException ex) {
      throw new AssertionError(ex);
    }
  }

  private static char mnemonic(String label) {
    int at = label.indexOf('&');
    assertTrue(label, at >= 0 && at + 1 < label.length());
    return Character.toLowerCase(label.charAt(at + 1));
  }
}
