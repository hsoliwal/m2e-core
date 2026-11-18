/*
 * SPDX-FileCopyrightText: 2026 Contributors to the Synexia Project
 * SPDX-License-Identifier: EPL-2.0
 */
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Standalone JDK 21 acceptance verifier for the original m2e JDT PDE tests.
 * Failure/skip status is checked from the testcases, not Maven's overall exit
 * status (the existing all-platform workflow has a failure-ignore setting).
 *
 * Run as: java m3/tools/M3JdtTestReceiptMain.java <jdt-surefire-reports-dir>
 */
public final class M3JdtTestReceiptMain {
    private static final String TEST = "M3JdtCompilerFactsTest";
    private static final Set<String> REQUIRED = Set.of(
            "capturesResolvedJdtClasspathAndEffectiveCompilerOptions",
            "explicitJdtFactsExportIsIdempotentButNeverReplacesAnInvocation",
            "canonicalOriginalJdtPacketMatchesMavenConsumerFixture",
            "refusesForeignInvocationAndHonorsEclipseCancellation");

    private M3JdtTestReceiptMain() { }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 1) {
            throw new IllegalArgumentException("usage: M3JdtTestReceiptMain <JDT report directory>");
        }
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("missing original JDT surefire reports: " + root);
        }
        Set<String> seen = new HashSet<>();
        int files = 0;
        try (Stream<Path> listing = Files.list(root)) {
            for (Path xml : listing.filter(p -> p.getFileName().toString().endsWith(".xml"))
                    .sorted().limit(4097).toList()) {
                if (++files > 4096) {
                    throw new IllegalArgumentException("JDT result file budget exceeded");
                }
                if (!Files.isRegularFile(xml, LinkOption.NOFOLLOW_LINKS)
                        || Files.size(xml) > 16 * 1024 * 1024) {
                    throw new IllegalArgumentException("unsafe JDT result file: " + xml);
                }
                var factory = DocumentBuilderFactory.newInstance();
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                factory.setXIncludeAware(false);
                factory.setExpandEntityReferences(false);
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                var document = factory.newDocumentBuilder().parse(xml.toFile());
                NodeList tests = document.getElementsByTagName("testcase");
                for (int i = 0; i < tests.getLength(); i++) {
                    Element test = (Element) tests.item(i);
                    String owner = test.getAttribute("classname");
                    if (!(owner.equals(TEST) || owner.endsWith("." + TEST))) continue;
                    String name = test.getAttribute("name");
                    if (!REQUIRED.contains(name) || !seen.add(name)) {
                        throw new IllegalArgumentException("unexpected or repeated JDT owner test: " + name);
                    }
                    for (String prohibited : new String[] {"failure", "error", "skipped"}) {
                        if (test.getElementsByTagName(prohibited).getLength() != 0) {
                            throw new IllegalArgumentException("JDT test was not accepted: " + name
                                    + " (" + prohibited + ")");
                        }
                    }
                }
            }
        }
        if (!seen.equals(REQUIRED)) {
            Set<String> missing = new HashSet<>(REQUIRED);
            missing.removeAll(seen);
            throw new IllegalArgumentException("original JDT PDE acceptance incomplete: " + missing);
        }
        System.out.println("PASS: " + seen.size() + " original JDT compiler facts tests executed without skips");
    }
}
