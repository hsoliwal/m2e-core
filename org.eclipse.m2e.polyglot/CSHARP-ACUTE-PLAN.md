<!--
  Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
  SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
-->
# C# in Eclipse through Maven: how the polyglot connector meets aCute

Date: 2026-11-17. This is the plan for the `aCute` row of `ECLIPSE_LANGUAGE_PLUGINS.tsv` (coverage matrix row
`csharp`). It records what is connected now, what each next step changes, and how to prove it. Nothing in this
plan was run in Eclipse: no PDE, Tycho or language server run was made for it.

## What Maven does for a .NET folder

The Maven fork's polyglot plugin (hsoliwal/maven#158) mavenizes a folder that has a `.sln`, `.slnx`, `.csproj`,
`.fsproj` or `.vbproj`. It writes an `m3-build.tsv` whose tasks run in these Maven phases:

| Phase | Task | Tool in the manifest |
| --- | --- | --- |
| validate | `dotnet restore` (`--locked-mode` next to `packages.lock.json`) | `dotnet` |
| compile | `dotnet build` | `dotnet` |
| test | `dotnet test --logger trx`; TRX becomes `target/surefire-reports/TEST-<class>.xml` | `@m3:surefire:interpreter:trx` |
| package | `dotnet pack --output target/nuget`; the `attach` goal attaches each `.nupkg` (classifier: package id) | `dotnet` |

## What this connector does today

- The `dotnet` task rows make the configurator look for a C# server (`language-servers.tsv`: `dotnet` maps to
  `csharp`, served by `csharp-ls`). The `@m3:surefire:...` row maps to no language, which is intended: its
  language is the TRX report format.
- When aCute is installed, a `.cs` file has aCute's content type `org.eclipse.acute.csharp` (priority normal),
  which beats this bundle's `org.eclipse.m2e.polyglot.csharp` (priority low). aCute's LSP4E server
  `org.eclipse.acute.roslynLS` already serves that content type, so `LanguageServerBinder` reports
  `ALREADY_SERVED` and adds nothing. aCute keeps the editor, the Roslyn language server, its `dotnet new`
  wizard and its run and test launches. This is the same precedence that applies to CDT with cdt-lsp and to
  Corrosion.
- Without aCute, `csharp-ls` on `PATH` is bound to `.cs` files in the Generic Editor. Without either, nothing is
  bound and the configurator logs it.
- m2e maps the polyglot `run` and `plan` executions to this configurator. Eclipse's incremental build therefore
  never runs `dotnet`: Maven does.

## Next steps

| Step | Change | Owner | Proof |
| --- | --- | --- | --- |
| 1. One build, not two | aCute's `org.eclipse.acute.builder.IncrementalDotnetBuilder` is on a project only after its *Add .NET builder* command. When that builder is on a project whose Maven build runs `dotnet` tasks, the configurator logs a warning that both build the same projects. It does not remove the builder. | this bundle (`PolyglotProjectConfigurator`) | PDE test: a project whose `.project` has the builder yields the warning; one without it does not |
| 2. Roslyn server without aCute | Add a second `server csharp` row for Roslyn LS (`Microsoft.CodeAnalysis.LanguageServer`) after `csharp-ls`, once its launch arguments are pinned from aCute's `RoslynLSConnectionProvider` (it needs log-level and log-folder arguments besides `--stdio`) | `language-servers.tsv` | `LanguageServerCatalogTest` for the two alternatives; a PDE binder test with each server on `PATH` |
| 3. Test results in the JUnit view | Maven's `target/surefire-reports/TEST-*.xml` are JUnit XML. Eclipse's JUnit view imports one such file through *Import JUnit test run*; automatic import after *Run As > Maven test* is m2e work that does not exist for Java either | none planned here | none |
| 4. C# scripts | Add `csx` to the `org.eclipse.m2e.polyglot.csharp` content type, as the Maven fork's `csharp` LSP profile maps `cs` and `csx` | `plugin.xml` | content-type test in `LanguageServerBinderTest` |
| 5. F# and Visual Basic | `.fsproj` and `.vbproj` folders are mavenized, but there is no server row: add `fsautocomplete` for F# (coverage wave W2) | `language-servers.tsv` | catalog test |
| 6. Project facts | The Maven fork's read-only `EclipseProjectModel` has no `.csproj` facts yet (target framework, package id, test project flag) | hsoliwal/maven polyglot plugin | its contract test |
| 7. NuGet packages | Attached `.nupkg` artifacts reach the local Maven repository with `install`. m2e has no NuGet resolution, and aCute resolves packages through `dotnet restore`. | none | none |

## ECJ, for comparison

Inside Eclipse, JDT already compiles Java with ECJ. A Maven build that picks `@m3:ecj:compiler:java`
(hsoliwal/maven#158) compiles with the same compiler. Passing `-properties .settings/org.eclipse.jdt.core.prefs`
makes it use the same compiler settings as the IDE. Both compilers' diagnostics become the same report rows
(hsoliwal/maven#160).

## Sources read

- aCute `org.eclipse.acute/plugin.xml` at eclipse-acute/aCute 95cace3a5559 (2026-10-07): content types
  `org.eclipse.acute.csharp` (`cs`, priority normal) and `org.eclipse.acute.csproj`; LSP4E servers
  `org.eclipse.acute.roslynLS` and `org.eclipse.acute.msbuild`; builder
  `org.eclipse.acute.builder.IncrementalDotnetBuilder`; launch types for `dotnet run` and `dotnet test`.
- This bundle: `language-servers.tsv`, `plugin.xml`, `LanguageServerBinder`, `PolyglotManifest` (tool names keep
  their `@m3:` form, so provider tasks map to no server unless a row names them).
