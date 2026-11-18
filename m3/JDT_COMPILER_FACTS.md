# Original Eclipse JDT compiler facts (experimental)

The `org.eclipse.m2e.jdt.M3JdtCompilerFacts` class captures the original
JDT Java project model, not a Maven/Gradle reconstruction of that model.

Its explicit `capture(IJavaProject, M3EclipseBuildHandoff.Snapshot,
IProgressMonitor)` requires the original `IJavaProject` and a prior
Eclipse owner handoff for **the same project and selected build
configuration**. It reads `getRawClasspath()`,
`getResolvedClasspath(false)`, `getOptions(true)`, and
`getOutputLocation()`. If JDT cannot resolve a classpath entry or
Eclipse cancels, capture fails rather than inventing a complete model.

The source-owner digests use SHA-256 over length-prefixed UTF-8. The raw
and resolved classpath digests include original order, entry/content
kinds, exported/test flags, source attachment/root, JDT output location,
inclusion/exclusion patterns, access-rule patterns/kinds, and classpath
attributes. The compiler-options digest uses all inherited JDT options
in sorted key order. The original project output path and exact Eclipse
invocation UUID are also preserved. All lists and values are bounded.

This information is **not** a digest of classpath JAR bytes, actual
annotation-processor effects, generated source files, ECJ native state,
Maven dependencies, a successful Java compiler execution, or a
build-cache admission certificate. Do not skip javac/ECJ, Maven goals,
or Gradle tasks on these fields alone.

## Original owner exchange

The source-owned packet is strict UTF-8 LF-only, versioned
`M3-JDT-COMPILER-FACTS-V1`:

```text
M3-JDT-COMPILER-FACTS-V1
provider	eclipse.jdt
project64	<canonical base64url original Eclipse project>
invocation	<original handoff UUID>
selected64	<canonical base64url selected IBuildConfiguration>
output64	<canonical base64url JDT output location>
rawSha256	<64 lowercase hex digits>
resolvedSha256	<64 lowercase hex digits>
optionsSha256	<64 lowercase hex digits>
rawCount	<canonical nonnegative integer>
resolvedCount	<canonical nonnegative integer>
```

Export only when requested by an authorized workspace workflow:

```java
var handoff = (M3EclipseBuildHandoff.Snapshot)
    facade.getSessionProperty(M3EclipseBuildHandoff.PROPERTY);
if (handoff != null) {
    var jdt = JavaCore.create(facade.getProject());
    var evidence = M3JdtCompilerFacts.capture(jdt, handoff, monitor);
    M3JdtCompilerFacts.writeSnapshot(evidence,
        facade.getProject().getLocation().toFile().toPath(),
        Path.of("target/ide/jdt-compiler-facts.ide"));
}
```

The parent `target/ide` must exist; files are project-local,
symlink paths and conflicting overwrites are refused. The export
is **never automatic** during normal Eclipse imports or builds.

The Maven-owned Java 17 decoder validates canonical byte encoding,
and refuses a JDT project, selected configuration or invocation ID
that differs from the original `M3-ECLIPSE-BUILD-HANDOFF-V1`.
The Gradle `m3EclipseJdtFacts` task delegates to that exact pinned
Maven decoder. Neither host implements a second JDT classpath resolver.

## Qualification

`M3JdtCompilerFactsTest` imports an actual JDT Maven fixture and
verifies stable classpath/compiler option digests, option-change
invalidation, matching project and invocation, cancellation, export
idempotence, conflicting generations and out-of-project refusal.
Maven's offline codec tests and Gradle's pinned-proxy tests remain
separate. Real PDE/OSGi runtime tests must pass before declaring
this feature fully qualified.
