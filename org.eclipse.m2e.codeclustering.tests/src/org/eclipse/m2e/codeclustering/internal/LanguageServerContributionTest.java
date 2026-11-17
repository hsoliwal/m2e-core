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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


/**
 * Checks the bundle's plugin.xml: the language server, the content types of the JDT, CDT, PyDev, WTP, Wild Web
 * Developer, Corrosion and RustDT editors, and the Maven project enablement of every mapping.
 */
public class LanguageServerContributionTest {

  private static final String SERVER_ID = "org.eclipse.m2e.codeclustering.server";

  private static final String DEFINITION_ID = "org.eclipse.m2e.codeclustering.mavenProjectFile";

  /** Content type to language id; each id was found as a content-type declaration in its project's plugin.xml. */
  private static final Map<String, String> CONTENT_TYPES = new LinkedHashMap<>();

  static {
    CONTENT_TYPES.put("org.eclipse.jdt.core.javaSource", "java");
    CONTENT_TYPES.put("org.eclipse.cdt.core.cSource", "c");
    CONTENT_TYPES.put("org.eclipse.cdt.core.cHeader", "c");
    CONTENT_TYPES.put("org.eclipse.cdt.core.cxxSource", "cpp");
    CONTENT_TYPES.put("org.eclipse.cdt.core.cxxHeader", "cpp");
    CONTENT_TYPES.put("org.python.pydev.pythonfile", "python");
    CONTENT_TYPES.put("org.eclipse.core.runtime.xml", "xml");
    CONTENT_TYPES.put("org.eclipse.wst.xml.core.xmlsource", "xml");
    CONTENT_TYPES.put("org.eclipse.wst.html.core.htmlsource", "html");
    CONTENT_TYPES.put("org.eclipse.wst.css.core.csssource", "css");
    CONTENT_TYPES.put("org.eclipse.wst.json.core.jsonsource", "json");
    CONTENT_TYPES.put("org.eclipse.wst.jsdt.core.jsSource", "javascript");
    CONTENT_TYPES.put("org.eclipse.tm4e.language_pack.html", "html");
    CONTENT_TYPES.put("org.eclipse.tm4e.language_pack.css", "css");
    CONTENT_TYPES.put("org.eclipse.tm4e.language_pack.json", "json");
    CONTENT_TYPES.put("org.eclipse.wildwebdeveloper.json", "json");
    CONTENT_TYPES.put("org.eclipse.tm4e.language_pack.javascript", "javascript");
    CONTENT_TYPES.put("org.eclipse.tm4e.language_pack.javascriptreact", "javascriptreact");
    CONTENT_TYPES.put("org.eclipse.tm4e.language_pack.typescript", "typescript");
    CONTENT_TYPES.put("org.eclipse.tm4e.language_pack.typescriptreact", "typescriptreact");
    CONTENT_TYPES.put("org.eclipse.tm4e.language_pack.rust", "rust");
    CONTENT_TYPES.put("com.github.rustdt.ide.core.content.source", "rust");
  }

  private static Document plugin;

  @BeforeClass
  public static void readPluginXml() throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    try (InputStream in = new ByteArrayInputStream(pluginXml())) {
      plugin = factory.newDocumentBuilder().parse(in);
    }
  }

  /**
   * The host bundle's plugin.xml: the one on the class path that declares the server (other bundles on a plain class
   * path have a plugin.xml too), else the bundle folder next to this fragment.
   */
  private static byte[] pluginXml() throws Exception {
    Enumeration<URL> resources = LanguageServerContributionTest.class.getClassLoader().getResources("plugin.xml");
    while(resources.hasMoreElements()) {
      try (InputStream in = resources.nextElement().openStream()) {
        byte[] bytes = in.readAllBytes();
        if(new String(bytes, StandardCharsets.UTF_8).contains("id=\"" + SERVER_ID + "\"")) {
          return bytes;
        }
      }
    }
    return Files.readAllBytes(Path.of("..", "org.eclipse.m2e.codeclustering", "plugin.xml"));
  }

  private static List<Element> elements(Node parent, String name) {
    NodeList nodes = parent instanceof Document d ? d.getElementsByTagName(name)
        : ((Element) parent).getElementsByTagName(name);
    List<Element> result = new ArrayList<>();
    for(int i = 0; i < nodes.getLength(); i++ ) {
      result.add((Element) nodes.item(i));
    }
    return result;
  }

  private static Element extension(String point) {
    return elements(plugin, "extension").stream().filter(e -> point.equals(e.getAttribute("point"))).findFirst()
        .orElseThrow(() -> new AssertionError("no extension of " + point));
  }

  @Test
  public void testServerDeclaration() {
    List<Element> servers = elements(extension("org.eclipse.lsp4e.languageServer"), "server");
    assertEquals(1, servers.size());
    Element server = servers.get(0);
    assertEquals(SERVER_ID, server.getAttribute("id"));
    assertEquals(ClusteringConnectionProvider.class.getName(), server.getAttribute("class"));
    assertEquals(MavenProjectsLanguageClient.class.getName(), server.getAttribute("clientImpl"));
    assertEquals("true", server.getAttribute("singleton"));
    assertEquals("org.eclipse.m2e.codeclustering.duplicate", server.getAttribute("markerType"));
  }

  @Test
  public void testContentTypesOfTheLanguageCores() {
    Map<String, String> mapped = new LinkedHashMap<>();
    for(Element mapping : elements(extension("org.eclipse.lsp4e.languageServer"), "contentTypeMapping")) {
      assertEquals(SERVER_ID, mapping.getAttribute("id"));
      assertEquals("duplicate mapping " + mapping.getAttribute("contentType"), null,
          mapped.put(mapping.getAttribute("contentType"), mapping.getAttribute("languageId")));
    }
    assertEquals(CONTENT_TYPES, mapped);
  }

  @Test
  public void testEveryMappingRequiresAMavenProject() {
    for(Element mapping : elements(extension("org.eclipse.lsp4e.languageServer"), "contentTypeMapping")) {
      List<Element> enabledWhen = elements(mapping, "enabledWhen");
      assertEquals(mapping.getAttribute("contentType"), 1, enabledWhen.size());
      List<Element> references = elements(enabledWhen.get(0), "reference");
      assertEquals(1, references.size());
      assertEquals(DEFINITION_ID, references.get(0).getAttribute("definitionId"));
    }
    Element definition = elements(extension("org.eclipse.core.expressions.definitions"), "definition").get(0);
    assertEquals(DEFINITION_ID, definition.getAttribute("id"));
    List<String> tests = elements(definition, "test").stream()
        .map(t -> t.getAttribute("property") + "=" + t.getAttribute("value")).toList();
    assertEquals(List.of("org.eclipse.m2e.codeclustering.enabled=",
        "org.eclipse.core.resources.projectNature=org.eclipse.m2e.core.maven2Nature"), tests);
  }

  @Test
  public void testPropertyTesterAndMarker() {
    Element tester = elements(extension("org.eclipse.core.expressions.propertyTesters"), "propertyTester").get(0);
    assertEquals(ClusteringPropertyTester.class.getName(), tester.getAttribute("class"));
    assertEquals("org.eclipse.m2e.codeclustering", tester.getAttribute("namespace"));
    assertEquals(ServerOptions.ENABLED, tester.getAttribute("properties"));
    Element marker = extension("org.eclipse.core.resources.markers");
    assertEquals("duplicate", marker.getAttribute("id"));
    assertEquals("org.eclipse.lsp4e.diagnostic", elements(marker, "super").get(0).getAttribute("type"));
    Element persistent = elements(marker, "persistent").get(0);
    assertNotNull(persistent);
    assertTrue("diagnostic markers must not persist", "false".equals(persistent.getAttribute("value")));
  }
}
