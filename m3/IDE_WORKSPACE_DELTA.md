# M3 workspace delta capture for Eclipse m2e (EPL-2.0)

This is an **additive**, opt-in m2e adapter. It captures Eclipse's original
`IResourceDelta` and publishes an immutable, lexicographically ordered snapshot
into `IMavenProjectFacade`'s **transient session properties** under
`M3WorkspaceDeltaBuildParticipant.PROPERTY`.

The capture class does not depend on Maven 4, M3JDK21, an AST, or a second file
watcher. It does not invoke Maven goals or JDT. Build participant registration is
explicit and **not installed automatically**: existing lifecycle mapping providers
can choose to instantiate `M3WorkspaceDeltaBuildParticipant` beside their original
participants without replacing their original goal execution.

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

A separate Visual Studio adapter can invoke Maven's normal lifecycle via an
explicit MSBuild target or Open Folder `tasks.vs.json`. This m2e helper is not a
VSIX, nor an IDE plugin installation.
