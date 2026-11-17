# M2E polyglot connector

A Maven project whose build compiles languages that Eclipse has no Maven connector for opens in
Eclipse with a language server for each of those languages. The connector does not compile
anything: Maven and the native tools remain the authority, and Maven keeps running them.

Two kinds of builds are recognised:

- a build that runs other tools from a polyglot task manifest (format `M3-POLYGLOT-TASKS-V1`);
- a build that compiles Groovy with GMavenPlus or Kotlin with the Kotlin Maven plug-in.

## How a Maven plugin opts in

The plugin that runs the manifest maps its executions to
`org.eclipse.m2e.polyglot.configurator`. It does this in the m2e lifecycle mapping
metadata embedded in its own jar (`META-INF/m2e/lifecycle-mapping-metadata.xml`). Its
`manifest` parameter names the task manifest. The polyglot plugin of the Maven fork
ships this mapping for its `run` and `plan` goals, and ignores `mavenize`. m2e then reports
no "not covered by lifecycle configuration" error, and the Eclipse incremental build does
not run the tools.

## Language Maven plug-ins

This bundle's own `lifecycle-mapping-metadata.xml` maps the lifecycle goals of two plug-ins
to `org.eclipse.m2e.polyglot.languagePlugins`:

| Plug-in | Versions | Goals |
|---------|----------|-------|
| `org.codehaus.gmavenplus:gmavenplus-plugin` | 1.0 to 1.5 | `addSources`, `addTestSources`, `generateStubs`, `compile`, `removeStubs`, `testGenerateStubs`, `testCompile`, `removeTestStubs` |
| `org.codehaus.gmavenplus:gmavenplus-plugin` | 1.6 and later | `addSources`, `addTestSources`, `addStubSources`, `addTestStubSources`, `generateStubs`, `generateTestStubs`, `compile`, `compileTests`, `removeStubs`, `removeTestStubs` |
| `org.jetbrains.kotlin:kotlin-maven-plugin` | 1.0.0 and later | `compile`, `test-compile`, `kapt`, `test-kapt` |

Such a project imports without lifecycle problems and gets a Groovy or Kotlin language server.
Eclipse does not compile the Groovy or Kotlin sources; Maven does.

Groovy-Eclipse and Kotlin for Eclipse map some of the same goals to their own configurators.
`org.eclipse.m2e.polyglot.languagePlugins` declares that it runs after both, as optional
predecessors. When either IDE is installed, its configurator stays the primary mapping and this
one becomes secondary, so m2e reports no duplicate mapping. When neither is installed, this one is
the primary mapping.

The `xml-maven-plugin` is not mapped: its jar embeds its own mapping (`execute`), which a bundle
mapping would override.

## What happens on import or *Maven > Update Project*

1. The configurator collects the languages of its executions: the tools of every `task` row of
   each mapped task manifest, and the languages of each mapped language plug-in.
2. `language-servers.tsv` maps each tool and plug-in to its languages, and each language to a
   sample file name and one or more language server commands, in order of preference:

   | Tools or plug-ins | Language | Servers |
   |-------------------|----------|---------|
   | cargo | Rust | `rust-analyzer` |
   | go | Go | `gopls` |
   | make, cmake, meson, ctest | C, C++ | `clangd` |
   | npm, pnpm, yarn, bun | JavaScript, TypeScript | `typescript-language-server --stdio` |
   | python, python3, pytest | Python | `pylsp` |
   | dotnet | C# | `csharp-ls` |
   | swift | Swift | `sourcekit-lsp` |
   | xmllint, xsltproc | XML | `lemminx` |
   | groovy, groovyc, GMavenPlus | Groovy | `groovy-language-server` |
   | kotlin, kotlinc, Kotlin Maven plug-in | Kotlin | `kotlin-language-server`, else `kotlin-lsp --stdio` |
   | php, composer, phpunit | PHP | `phpactor language-server`, else `intelephense --stdio` |
   | cobc | COBOL | `cobol-language-support` |
   | erl, erlc, escript, rebar3 | Erlang | `elp server`, else `erlang_ls` |

   The command names are the ones the Mason registry installs. phpactor is MIT licensed and is
   preferred; intelephense is closed source with a free tier, and is used only when it is the
   one installed.
3. For each language, the configurator asks Eclipse which content type a file with the
   sample name has. It acts only when no LSP4E server serves that content type yet and one of the
   server commands is on `PATH`. It then creates an *External Tools* program launch
   configuration named `Language server: <command>` for the first command found, and registers
   it with LSP4E for that content type. This is the same association LSP4E's *Language Servers*
   preference page creates.

## Precedence

- An installed IDE wins: CDT with cdt-lsp, Corrosion, Wild Web Developer and other plug-ins
  keep their own content types and LSP4E servers. This bundle's content types have low priority,
  and the configurator never replaces an existing association.
- Groovy, Kotlin, PHP, COBOL and Erlang *yield* to plug-ins: their server is bound only to this
  bundle's own content type. When Groovy-Eclipse, Kotlin for Eclipse, PDT, Erlide or another
  plug-in claims the files, it keeps them, even without LSP4E (outcome `IDE_PLUGIN`).
- XML uses the platform XML content type. Where Wild Web Developer is installed (the m2e LemMinX
  feature requires it), LemMinX already serves XML and nothing is added.
- C# follows the same rule. With aCute installed, its `org.eclipse.acute.csharp` content type and
  Roslyn server keep `.cs` files; `csharp-ls` is bound only without aCute.
  [CSHARP-ACUTE-PLAN.md](CSHARP-ACUTE-PLAN.md) records what Maven does for .NET folders and the next steps.
- A server that is not on `PATH` is logged and skipped. Nothing is downloaded.
- Java stays with `org.eclipse.m2e.jdt`. Gradle, Ant and Bazel tools map to no server.

The bundle declares low-priority content types (based on text) for the languages above except
XML, and binds them to the Generic Editor. The LSP4E features therefore apply when no other
plug-in claims those files.

## Validation

`org.eclipse.m2e.polyglot.tests` checks:

- manifest parsing, including malformed rows;
- catalog parsing and selection: tools, plug-ins, alternatives in order, yielding languages,
  and inconsistent rows;
- the lifecycle mapping metadata, read with m2e's own reader and matched with m2e's own
  filters: which goals and versions are mapped, which plug-ins are left alone, and that the
  mapped plug-ins are exactly the catalog's `plugin` rows;
- the language plug-in configurator's optional `runsAfter` predecessors, and a low-priority
  content type with a Generic Editor binding for every yielding language;
- `PATH` resolution, including Windows `PATHEXT`;
- content-type resolution;
- the LSP4E association for a server on `PATH`, the first alternative found, no association
  when a server already serves the content type, and none for another plug-in's content type
  when the language yields.
