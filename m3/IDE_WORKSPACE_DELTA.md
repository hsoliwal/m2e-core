# M3 workspace delta capture for Eclipse m2e (EPL-2.0)

This is an **additive**, opt-in m2e adapter. It captures Eclipse's original
`IResourceDelta` and publishes an immutable, lexicographically ordered snapshot
into `IMavenProjectFacade`'s **transient session properties** under
`M3WorkspaceDeltaBuildParticipant.PROPERTY`.

There are two **mutually exclusive** opt-in paths:

- **Original m2e owner:** start Eclipse with JVM argument `-Dm3.ide.delta.capture=true`.
  The existing `MavenBuilderImpl` records the original `IResourceDelta` before its
  early return for irrelevant/marker-only changes. The switch is off by default.
  Capture errors leave the transient property empty, never count as success,
  and do not change the ordinary build's results or lifecycle scheduling.
  PRECONFIGURE builds do not publish a misleading unknown build-kind packet.
- **Existing lifecycle mapping provider:** explicitly register
  `M3WorkspaceDeltaBuildParticipant`, as before. Leave the system switch off
  when using this participant, to avoid duplicate capture in the same build.

Both modes publish `M3WorkspaceDelta.Snapshot` to the same transient project
session property. Consumers still must supply genuine build context and generation
metadata and can only receive advisory changes. No source watcher, new javac task,
Maven goal skip or persistent cache is introduced.

A new PDE integration test in `ExtensionsTest` exercises the owner switch with a
real m2e imported project and its original full build. Its Eclipse/Tycho execution
is required before this optional mode can be declared qualified.

The capture class does not depend on Maven 4, M3JDK21, an AST, or a second file
watcher. It does not invoke Maven goals or JDT. Nothing is enabled by default:
use the original-owner JVM switch or have a lifecycle mapping provider register
`M3WorkspaceDeltaBuildParticipant` explicitly, without replacing existing goals.

Consumers must bind project identity, Maven model, toolchain, dependencies,
source-root digests, and monotonically increasing generation numbers from their
**own verified host state** before calling `M3ExternalBuildService` in an M3 Maven
4 session. This adapter deliberately does not fabricate those digests or claim
Maven skip/cache eligibility. Full/clean and absent/incomplete deltas require
reconciliation. Renames retain their source owner's removed/added notifications;
marker-only events are ignored. Linked and derived resources are captured but
cause `deltaComplete=false`. Cancellation propagates to the native Eclipse owner.

Tests: `org.eclipse.m2e.core.tests/src/org/eclipse/m2e/core/M3WorkspaceDeltaTest.java`
validates sorting, marker-only changes, source removal, generated outputs,
unsafe project-relative names, cold builds and duplicate callbacks.

The `Snapshot.toExchangeBytes()` method exports only the owner-provided paths
and change kinds using `M3-IDE-DELTA-V1`, for the companion
`M3ExternalBuildWire.decode(...)` in Maven. It omits all fabricated model,
toolchain, dependency and source-root digests. Maven consumers must bind those
from independently verified build context, and must treat missing information as
reconciliation, not a cache hit.

## Explicit handoff to Maven 4

A client that explicitly wants Maven to inspect its **already captured** Eclipse
snapshot can write one versioned packet under the project root. This is an
additional opt-in method on the existing source owner, **not** an automatic
Eclipse builder side effect:

```java
var snapshot = (M3WorkspaceDelta.Snapshot)
    facade.getSessionProperty(M3WorkspaceDeltaBuildParticipant.PROPERTY);
if (snapshot != null) {
    M3WorkspaceDelta.writeSnapshot(snapshot, project.basedir(),
        Path.of("target/ide/changes.ide"));
}
```

The output parent directory must already exist. The method refuses out-of-project
paths, symlinks and conflicting destination bytes, and reuses an identical packet
without overwriting it. It does not create or advance a build-generation counter,
compute model/toolchain/dependency hashes, or launch Maven. A project can choose
a new path per generation instead of overwriting a previous packet. If the file
is visible to Eclipse, the caller is responsible for treating it as a derived
build artifact and avoiding recursive build notifications.

The Maven 4 `m3-ide-bridge-maven-plugin:ide-assess` goal added in Maven PR #106
reads these exact bytes and requires real externally supplied build context.
It never authorizes Maven goal skipping.

A separate Visual Studio adapter can invoke Maven's normal lifecycle via an
explicit MSBuild target or Open Folder `tasks.vs.json`. This m2e helper is not a
VSIX, nor an IDE plugin installation.
