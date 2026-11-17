# M2E code clustering language server

Reports duplicated code in Maven projects as problem markers, in the editors of the
Eclipse language cores, through Eclipse LSP4E:

| Editor | Content types |
|--------|---------------|
| JDT | `org.eclipse.jdt.core.javaSource` |
| CDT | `org.eclipse.cdt.core.cSource`, `cHeader`, `cxxSource`, `cxxHeader` |
| PyDev | `org.python.pydev.pythonfile` |
| WTP | `org.eclipse.wst.xml.core.xmlsource`, `org.eclipse.wst.html.core.htmlsource`, `org.eclipse.wst.css.core.csssource`, `org.eclipse.wst.json.core.jsonsource`, `org.eclipse.wst.jsdt.core.jsSource` |
| Wild Web Developer | `org.eclipse.core.runtime.xml`, `org.eclipse.wildwebdeveloper.json`, `org.eclipse.tm4e.language_pack.{html,css,json,javascript,javascriptreact,typescript,typescriptreact}` |
| Corrosion | `org.eclipse.tm4e.language_pack.rust` |
| RustDT (archived) | `com.github.rustdt.ide.core.content.source` |

Each id is declared in the plugin.xml of its project; this bundle's plugin.xml names the
commit and file.
LSP4E skips a mapping whose content type is not installed.

## How it runs

- The server is `org.apache.maven.plugins.clustering.lsp.ClusteringServerMain` from
  `org.apache.maven.plugins:maven-code-clustering-lsp`. The bundle resolves that artifact,
  `maven-code-clustering-plugin` (a `provided` dependency of the server) and their runtime
  dependencies through m2e: the local repository first, offline, then the repositories of
  `settings.xml` unless Maven is offline. Nothing is bundled.
- It starts the server with the Java that runs Eclipse. The server's javac front end needs
  the `jdk.compiler` module; on a JRE without it, set a JDK in the preferences.
- One server serves the workspace. Its client announces the open Maven projects as
  workspace folders, without modules that lie inside another announced project, which
  would otherwise be scanned twice and reported as duplicates of themselves.
- Diagnostics use the marker type `org.eclipse.m2e.codeclustering.duplicate`, a subtype of
  `org.eclipse.lsp4e.diagnostic`.
- Limitation: the workspace folders are fixed when the server starts. A Maven project that
  is imported or opened later is not scanned, and a closed one keeps being scanned, until
  the server restarts (close every mapped editor, or restart Eclipse). Folder change
  notifications are a planned server feature.
- XML, HTML, CSS and JSON files only get atoms through their language servers: set the
  language cores to `wtp` (LemMinX and the web language servers on the PATH). Without it the
  server starts for those files but reports nothing for them.

## Preferences (Maven > Code Clustering)

The server is off until **Enable** is set. A mapping applies only when the server is
enabled and the file belongs to a project with the Maven nature.

| Preference | Default | Meaning |
|------------|---------|---------|
| Server version | `0.1.0-SNAPSHOT` | version of both artifacts |
| Minimum atom size | 12 | sent as `minTokens` in the initialization options |
| Language cores | empty | `clustering.lsp.profiles`: `jdt`, `cdt`, `pydev`, `wtp`, `rust` or `all`; each lets the matching language server (JDT LS, clangd, pylsp, LemMinX and the web servers, rust-analyzer) split files into atoms; empty uses the built-in front ends |
| Explicit language servers | empty | `clustering.lsp.servers`, for example `c,h=clangd;rs=rust-analyzer` |
| Java | empty | a `java` executable or a JDK directory |
| JVM arguments | empty | extra arguments of the server's JVM |
| Server class path | empty | explicit jars; when set, nothing is resolved |

Changes apply when the server starts next.

## Lifecycle mapping

`org.eclipse.m2e.core/lifecycle-mapping-metadata.xml` maps the goals `cluster`, `compare`
and `snapshot` of `maven-code-clustering-plugin` to `ignore`: the reports are written by the
command-line build, and the language server shows the same results in the editors.

## Validation

`org.eclipse.m2e.codeclustering.tests` checks the version preference, the option parsing, the
command line, the workspace folder selection, and the language server contribution in
plugin.xml. These tests need no running workbench.
