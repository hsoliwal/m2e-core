# M2E polyglot connector

A Maven project whose build runs other tools from a polyglot task manifest (format
`M3-POLYGLOT-TASKS-V1`) opens in Eclipse with a language server for every language those
tools build. The connector does not compile anything: the native tools remain the
authority, and Maven keeps running them.

## How a Maven plugin opts in

The plugin that runs the manifest maps its executions to
`org.eclipse.m2e.polyglot.configurator`. It does this in the m2e lifecycle mapping
metadata embedded in its own jar (`META-INF/m2e/lifecycle-mapping-metadata.xml`). Its
`manifest` parameter names the task manifest. The polyglot plugin of the Maven fork
ships this mapping for its `run` and `plan` goals, and ignores `mavenize`. m2e then reports
no "not covered by lifecycle configuration" error, and the Eclipse incremental build does
not run the tools.

## What happens on import or *Maven > Update Project*

1. The configurator reads the manifest of every execution mapped to it and collects the
   tool of every `task` row.
2. `language-servers.tsv` maps each tool to its languages, and each language to a sample
   file name and a language server command:

   | Tools | Languages | Server |
   |-------|-----------|--------|
   | cargo | Rust | `rust-analyzer` |
   | go | Go | `gopls` |
   | make, cmake, meson, ctest | C, C++ | `clangd` |
   | npm, pnpm, yarn, bun | JavaScript, TypeScript | `typescript-language-server --stdio` |
   | python, python3, pytest | Python | `pylsp` |
   | dotnet | C# | `csharp-ls` |
   | swift | Swift | `sourcekit-lsp` |

3. For each language, the configurator asks Eclipse which content type a file with the
   sample name has. It acts only when no LSP4E server serves that content type yet and the
   server command is on `PATH`. It then creates an *External Tools* program launch
   configuration named `Language server: <command>`, and registers it with LSP4E for that
   content type. This is the same association LSP4E's *Language Servers* preference page
   creates.

## Precedence

- An installed IDE wins: CDT with cdt-lsp, Corrosion, and other plug-ins keep their own
  content types and LSP4E servers. This bundle's content types have low priority, and the
  configurator never replaces an existing association.
- A server that is not on `PATH` is logged and skipped. Nothing is downloaded.
- Java stays with `org.eclipse.m2e.jdt`. Gradle, Ant and Bazel tools map to no server.

The bundle declares low-priority content types (based on text) for the languages above,
and binds them to the Generic Editor. The LSP4E features therefore apply when no other
plug-in claims those files.

## Validation

`org.eclipse.m2e.polyglot.tests` checks:

- manifest parsing, including malformed rows;
- catalog selection;
- `PATH` resolution, including Windows `PATHEXT`;
- content-type resolution;
- the LSP4E association for a server on `PATH`, and no association when a server
  already serves the content type.
