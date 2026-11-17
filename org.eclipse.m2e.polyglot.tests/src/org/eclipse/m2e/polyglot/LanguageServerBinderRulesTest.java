/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0, or (at your option) the Eclipse
 * Public License 2.0, which are available at
 * https://www.apache.org/licenses/LICENSE-2.0 and
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.m2e.polyglot;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.eclipse.core.runtime.content.IContentType;
import org.junit.Test;

/** The binder's decisions that need no running platform: which servers count and which plug-ins keep a language. */
public class LanguageServerBinderRulesTest {

	private static final IContentType TEXT = contentType("org.eclipse.core.runtime.text", null);

	private static final IContentType RUST = contentType("org.eclipse.m2e.polyglot.rust", TEXT);

	private static final Predicate<String> WTP_XML = LanguageServerBinder.WTP_XML_UI::equals;

	private static final Predicate<String> NOTHING = bundle -> false;

	/** LSP4E starts a server registered on a base content type for its subtypes too. */
	@Test
	public void aServerOnABaseContentTypeServesItsSubtypes() {
		assertTrue(LanguageServerBinder.servedBy(RUST, Stream.of(RUST)));
		assertTrue(LanguageServerBinder.servedBy(RUST, Stream.of(TEXT)));
		assertFalse(LanguageServerBinder.servedBy(TEXT, Stream.of(RUST)));
		assertFalse(LanguageServerBinder.servedBy(RUST, Stream.of()));
	}

	/** WTP's XML editor serves XML without LSP4E, so lemminx is not bound next to it. */
	@Test
	public void xmlYieldsToTheWtpXmlEditor() {
		LanguageBinding xml = binding("xml", "file.xml", false);
		assertTrue(LanguageServerBinder.yieldsToPlugin(xml, "org.eclipse.core.runtime.xml", WTP_XML));
		assertFalse(LanguageServerBinder.yieldsToPlugin(xml, "org.eclipse.core.runtime.xml", NOTHING));
		assertFalse(LanguageServerBinder.yieldsToPlugin(binding("rust", "file.rs", false), RUST.getId(), WTP_XML));
	}

	@Test
	public void aYieldingLanguageKeepsOnlyThisBundlesContentType() {
		LanguageBinding groovy = binding("groovy", "file.groovy", true);
		assertFalse(LanguageServerBinder.yieldsToPlugin(groovy, "org.eclipse.m2e.polyglot.groovy", WTP_XML));
		assertTrue(LanguageServerBinder.yieldsToPlugin(groovy, "org.codehaus.groovy.eclipse.groovySource", NOTHING));
		assertFalse(LanguageServerBinder.yieldsToPlugin(binding("go", "file.go", false), "other.go", NOTHING));
	}

	private static LanguageBinding binding(String language, String sampleFile, boolean yields) {
		return new LanguageBinding(language, List.of(new LanguageServer(language, sampleFile, language + "-ls", List.of())),
				yields);
	}

	/** A content type with an id and a base type; {@code isKindOf} follows the base types, as Eclipse's does. */
	private static IContentType contentType(String id, IContentType base) {
		return (IContentType) Proxy.newProxyInstance(IContentType.class.getClassLoader(),
				new Class<?>[] { IContentType.class }, (proxy, method, args) -> switch (method.getName()) {
				case "getId", "toString" -> id;
				case "getBaseType" -> base;
				case "isKindOf" -> proxy == args[0] || base != null && base.isKindOf((IContentType) args[0]);
				case "equals" -> proxy == args[0];
				case "hashCode" -> System.identityHashCode(proxy);
				default -> throw new UnsupportedOperationException(method.getName());
				});
	}
}
