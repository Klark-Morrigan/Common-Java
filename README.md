# Common-Java

Shared build, CI and tooling scaffolding for JVM projects.
This repo holds no runtime library code and knows no consumer.
It is the generic tier that downstream projects build on.

## Index

- [What lives here](#what-lives-here)
- [Gradle conventions](#gradle-conventions)
  - [Test run report](#test-run-report)
  - [Checkstyle](#checkstyle)
- [Lint gates](#lint-gates)
  - [Declarations and allowances](#declarations-and-allowances)
  - [Formatting gates](#formatting-gates) -
    `enforceNoTrailingWhitespace`, `enforceSingleBlankLines`
  - [Markdown gates](#markdown-gates) -
    `enforceDocLinksResolve`, `enforceIdCasing`
    - [Forbidden phrases](#forbidden-phrases) -
      `enforceNoForbiddenPhrases`
  - [Test-tree gates](#test-tree-gates) -
    `enforceCamelCaseTestNames`, `enforceSuffixOnFakes`, `enforceSuffixOnMocks`, `enforceSuffixOnSuites`,
    `enforceTestNamesOmitGroup`, `enforceTestsNested`
  - [Method order and Kotlin literals](#method-order-and-kotlin-literals) -
    `enforceMethodsOrderedByVisibility`, `enforceNoMagicLiteralsKotlin`
  - [Test vocabulary](#test-vocabulary) -
    `enforceNoTestVocabulary`
  - [Package layering](#package-layering) -
    `enforcePackageLayering`
  - [Package vocabulary](#package-vocabulary) -
    `enforcePackageVocabulary`
  - [Restricted calls](#restricted-calls) -
    `enforceRestrictedCalls`
  - [Referenced types](#referenced-types) -
    `enforceReferencedTypes`
  - [Helper scripts](#helper-scripts)
- [AWT environment probe](#awt-environment-probe)
- [Formatting](#formatting)
  - [Excluded source sets](#excluded-source-sets)
- [Reusable CI](#reusable-ci)
- [Local scripts and hooks](#local-scripts-and-hooks)
- [Testing the scripts](#testing-the-scripts)
- [Consuming with a domain layer](#consuming-with-a-domain-layer)

## What lives here

- `gradle/java-conventions.gradle` - the generic JVM build conventions a consumer applies.
- `gradle/checkstyle.xml` - the Java checks the conventions run through Checkstyle.
- `gradle/detekt.yml` - the Kotlin magic-number rule `enforceNoMagicLiteralsKotlin` runs.
- `gradle/tasks/source-text.gradle` - the file types and encoding every script reading code agrees on.
- `gradle/tasks/lint/` - the lint gates the conventions apply, and their helpers.
- `gradle/tasks/diagnostics/` - reports about the machine and the test run rather than the code.
- `gradle/spotless-java.gradle`, `gradle/tasks/format/` - the opt-in, on-demand source cleanup.
- `ci-smoke/` - a Gradle project that applies the conventions as a consumer would,
  and holds the TestKit suites for every script.
- `.github/workflows/_ci-gradle.yml` - the reusable Gradle build and test workflow.
- `.github/workflows/ci-gradle.yml` - this repo's own caller of that workflow, building `ci-smoke/`.
- `.github/workflows/ci-bash.yml`, `ci-yaml.yml` - callers that delegate shell and YAML linting to Common-Automation.
- `.github/lib/` - the JVM tier's declarations for the shared pre-commit fixes:
  `executable-paths.sh` and `trimmed-file-types.sh`.
- `.githooks/pre-commit` - this repo's commit hook.
- `scripts/` - local runners: shims to Common-Automation's engines,
  and `run-ci-gradle.sh`, the Gradle engine consumer repos call.

## Gradle conventions

[gradle/java-conventions.gradle](gradle/java-conventions.gradle) sets:

- Java 17 as the compile release, with UTF-8 sources.
- JUnit 5, AssertJ and Mockito as the test stack, pinned in one place.
- JaCoCo coverage, with HTML, XML and CSV reports and a `coverage` task that runs the tests first.
- For a project applying `java-test-fixtures`: the same test stack for the fixtures, and coverage over them.
- Checkstyle, the lint gates and the AWT probe described below.

A consumer applies it by path:

```groovy
apply from: "${rootDir}/../Common-Java/gradle/java-conventions.gradle"
```

The path assumes consumers are checked out as siblings under the same parent directory.
No `settings.gradle` change is needed.

Each configured project logs the Gradle version and the JVM it ran on.

### Test run report

Every test run ends with the ten slowest tests.
A failing run then lists the failures twice:
first the names, then the same names under the same numbers, each with its stack trace and full cause chain.
Both blocks sit at the bottom of the log, where a reader starts.
Traces stop after the first ten failures;
the rest are in the task's HTML test report.

A `Test` task registered beside `test`, such as a TestKit tree, asks for the same report:

```groovy
reportTestOutcomesOf(it)
```

### Checkstyle

Checkstyle runs from [gradle/checkstyle.xml](gradle/checkstyle.xml).
It reports unused, redundant, wildcard and misordered imports, unused locals, and magic numbers.
Magic numbers are not checked under `src/test`.
Violations fail the build.

The import-order rule takes its blocks as properties.
The default is everything, then the JDK.
A consumer, or a family conventions script above this one, states its real blocks:

```groovy
def importGroups = 'com.example.api,mylib,myapp,/^org\\./,java'

checkstyle.configProperties.importGroups = importGroups
checkstyle.configProperties.staticImportGroups = importGroups
```

Every package a project imports from must match a group.
Checkstyle sorts an unmatched import after every named group, below `java.*`,
so a missing group fails as a misordered import.

## Lint gates

`java-conventions.gradle` applies every gate under [gradle/tasks/lint/](gradle/tasks/lint/).
Each asserts one convention no compiler reports.
`test` and `check` depend on every gate.
A gate is skipped as up to date while its inputs and rules are unchanged.

| Task | Reads | Rule |
| --- | --- | --- |
| `enforceCamelCaseTestNames` | test trees | methods are named in camelCase, not snake_case |
| `enforceDocLinksResolve` | every `.md` file in the project | a relative link points at a file that exists, spelt in the same case |
| `enforceIdCasing` | every `.md` file, and Java and Kotlin comments under `src/` | prose spells the abbreviation `ID`, not `id` |
| `enforceMethodsOrderedByVisibility` | Java and Kotlin under `src/main` | methods in a type run most-public-first |
| `enforceNoForbiddenPhrases` | the `.md` files each rule names | prose does not say a phrase the build rules out of those files |
| `enforceNoMagicLiteralsKotlin` | Kotlin in every source set but `test` | numbers are named constants |
| `enforceNoTestVocabulary` | Java, Kotlin and `.md` under `src/`, test trees exempt | production sources do not use the words of a test suite |
| `enforceNoTrailingWhitespace` | Java and Kotlin in the formatted source sets | no line ends in whitespace |
| `enforcePackageLayering` | Java and Kotlin under `src/` | a package does not import a layer it is declared closed to |
| `enforcePackageVocabulary` | Java, Kotlin and `.md` under `src/` | a package root does not say a word it is declared closed to |
| `enforceReferencedTypes` | compiled classes under `build/classes` | code names a restricted namespace's types only where they are allowed |
| `enforceRestrictedCalls` | Java and Kotlin under `src/` | a restricted call is made only in the types named for it |
| `enforceSingleBlankLines` | Java and Kotlin in the formatted source sets | at most one consecutive blank line |
| `enforceSuffixOnFakes` | test trees | hand-written test doubles and their holders end in `Fake` |
| `enforceSuffixOnMocks` | test trees | holders of generated mocks and spies end in `Mock` |
| `enforceSuffixOnSuites` | test trees | a class holding tests ends in `Tests` |
| `enforceTestNamesOmitGroup` | test trees | a test method's name does not repeat its `@Nested` group's |
| `enforceTestsNested` | test trees | every test method sits inside a `@Nested` class JUnit runs |

The test trees are `src/test` and `src/testFixtures`.

### Declarations and allowances

Five gates check nothing until a consumer declares rules:
`enforcePackageLayering`, `enforcePackageVocabulary`, `enforceRestrictedCalls`, `enforceReferencedTypes`
and `enforceNoForbiddenPhrases`.
Which package may reach which, which words or calls it may use, and which phrases its documents may say,
is a per-project fact this repo cannot know.

A declaration takes named arguments and states a whole rule in one call.
A malformed declaration fails where it is written.
Where a gate offers allowances or exemptions,
each is a line in the build that a reviewer reads in the diff,
never an inline marker in the source.

A stranded allowance fails the build;
each gate's section says when one is stranded.
A stale entry reads as a rule while exempting nothing,
and the text it was written for is unguarded again the moment it returns under another name.

### Formatting gates

`enforceNoTrailingWhitespace` and `enforceSingleBlankLines` read the Java and Kotlin of every source set,
less those named in [`formatterExcludedSourceSets`](#excluded-source-sets).
A line holding only whitespace counts as blank.
Both need the `java` plugin, which the conventions apply.

### Markdown gates

`enforceDocLinksResolve` and `enforceIdCasing` read every `.md` file in the project,
not only those under `src/`, because a repository's longest prose is its root README.
Both skip directories named in `authoredScanExcludedDirectoryNames`.
Its defaults are version-control and build-tool directories:
`.git`, `.gradle`, `bin`, `build`, `out` and `node_modules`.
A consumer whose own tooling writes Markdown adds that directory.
Additions made after the gates are applied still count:

```groovy
authoredScanExcludedDirectoryNames << 'generated-docs'
```

`enforceDocLinksResolve` checks inline links outside fenced code blocks.
A target with a scheme or `//host`, a target above the project root, and a pure `#fragment` are out of scope.
A target that exists only in another case fails too,
since it resolves on a case-insensitive disk and breaks on a repository host.

`enforceIdCasing` owns its whole rule, since the spelling of an abbreviation is the same in every repository.
It reads only prose:
code spans, `{@code}` spans, `<pre>` samples, fenced blocks, `@param` names and dotted, hyphenated or underscored spellings are left alone.
In Java and Kotlin it reads comments only, so it never asks for a rename.
See [the script](gradle/tasks/lint/naming/enforce-id-casing.gradle) for the full list.
A consumer exempts prose it carries without authoring:

```groovy
enforceIdCasing {
    exemptPath glob: 'docs/vendor/**/*.md'
}
```

An `exemptPath` matching no file fails the build.

#### Forbidden phrases

`enforceNoForbiddenPhrases` keeps a phrase out of the documents a consumer names.
It fits a habit of writing a reviewer keeps striking,
such as a stock justification that points at the wrong source,
which no compiler or test would ever report.

```groovy
enforceNoForbiddenPhrases {
    forbidPhrase pattern: /(?i)\bas the manual says\b/,
        inFiles: 'docs/reference/*.md',
        reason: 'cites the manual as the reason'
}
```

- `pattern:` is a regular expression, matched against one line at a time.
  Flags such as `(?i)` go inside it.
  A pattern that does not compile fails where it is declared.
- `inFiles:` takes one project-relative glob or a list.
  It is matched against the authored Markdown the other Markdown gates read.
- `reason:` is written into each finding,
  between the file and line and the phrase found.

Only prose is judged:
a fenced block or an inline code span is a quoted sample, so a phrase inside one passes.
A glob that matches no file checks nothing and does not fail,
since a family's conventions may declare a rule for documents only some of its projects carry.

### Test-tree gates

These gates read `src/test` and `src/testFixtures`.

- `enforceCamelCaseTestNames` reports any snake_case name that opens a call,
  which covers test methods and helpers alike.
  Kotlin backtick names, constants and literals are left alone.
- `enforceSuffixOnFakes` reports a type with `Fake` in its name that does not end in it,
  and a variable built from a `*Fake(...)` call that is not suffixed `Fake`, or `_FAKE` for a constant.
- `enforceSuffixOnMocks` reports a holder not suffixed `Mock`, or `_MOCK` for a constant.
  A holder is a variable assigned from Mockito's `mock`, `mockStatic`, `mockConstruction` or `spy`,
  or MockK's `mockk` or `spyk`,
  or a field annotated `@Mock`, `@Spy`, `@MockK`, `@RelaxedMockK` or `@SpyK`.
- `enforceSuffixOnSuites` reports an outermost class that holds a test or a `@Nested` group and does not end in `Tests`.
  Name an integration suite `<Subject>IntegrationTests`.
- `enforceTestNamesOmitGroup` reports a test name that opens with its `@Nested` group's name and goes on to another word,
  such as `readTotalIsZero` under `ReadTotal`.
- `enforceTestsNested` reports a test method outside a `@Nested` class.
  It also reports a `@Nested` class JUnit skips:
  a Kotlin one not declared `inner`, and a Java one declared `static`.

### Method order and Kotlin literals

`enforceMethodsOrderedByVisibility` reads `src/main` only.
Within a type, methods descend the visibility ladder:
`public`, `protected`, package-private, `private` in Java,
and `public`, `internal`, `protected`, `private` in Kotlin.
An unmodified Java method is package-private, or public inside an interface;
an unmodified Kotlin method is public.
Constructors, enum constants and top-level Kotlin functions are not ranked.

`enforceNoMagicLiteralsKotlin` runs the detekt CLI with [gradle/detekt.yml](gradle/detekt.yml),
which enables only `MagicNumber` and turns config validation on.
It reads the Kotlin of every source set except `test`, and is skipped when there is none.
Java's magic numbers are Checkstyle's.

### Test vocabulary

`enforceNoTestVocabulary` keeps the words of a test suite out of production code and the Markdown beside it.
It owns its word list:
words like `mock`, `fake`, `stubbing` and `JUnit`,
and phrases like `unit test` and `test harness`.
See [the script](gradle/tasks/lint/naming/enforce-no-test-vocabulary.gradle) for the list.
Words are matched as in [package vocabulary](#package-vocabulary).

It reads every tree under `src/` except the test trees,
which are `test` and `testFixtures` by default.
A consumer declares only exemptions:

```groovy
enforceNoTestVocabulary {
    exemptSourceSet name: 'bridgeStubs'
    exemptPackage root: 'example.production.testsupport'
    allowWord word: 'mocking', inFile: 'src/main/java/example/production/README.md'
    allowWords words: ['mock', 'unit test'], inFile: 'src/main/java/example/production/Guide.md'
}
```

- `exemptSourceSet name:` exempts a whole tree, named as its directory under `src/`.
- `exemptPackage root:` exempts a package root wherever it sits,
  for fixtures that ship in a production source set.
- `allowWord word: inFile:` lets one project-relative file say one banned entry, spelt as the list spells it.
- `allowWords words: inFile:` does the same for a list of entries.

An allowance fails the build when its file is not scanned, sits in an exempt tree,
or its word is no banned entry.

### Package layering

`enforcePackageLayering` reads the imports of every Java and Kotlin file under `src/`, test code included.
Each `forbidImport` closes one side to one or more layers:

```groovy
enforcePackageLayering {
    forbidImport under: 'example.framework', of: 'example.feature'
    forbidImport under: 'example.framework.reads',
        of: ['example.framework.writes', 'example.framework.layout']
}
```

The closed side comes in two widths:

- `under:` closes a root and everything beneath it.
  `except:` leaves out subtrees of it, one package or a list, each strictly beneath the root.
- `exactly:` closes that one package and leaves its subpackages free.
  It takes no `except`.

Naming both widths, or an `except` outside its root, fails where it is written.

`of:` takes a root, a layer map `[under:, except:]`, or a list of either.
Each entry is its own edge.
A layer map closes a subtree to the rest of its parent without closing it to itself:

```groovy
enforcePackageLayering {
    forbidImport under: 'example.framework.kinds',
        of: [under: 'example.framework', except: 'example.framework.kinds']
}
```

An `except` on the closed side fits a framework whose seam fillers live inside it:
the rest of the framework may not reach them, while they reach each other freely.

```groovy
enforcePackageLayering {
    forbidImport under: 'example.framework',
        except: 'example.framework.kinds',
        of: 'example.framework.kinds'
}
```

Commented-out imports and imports spelt inside strings are not judged.

### Package vocabulary

`enforcePackageVocabulary` keeps a package root from being written in another's words:
in code, in comments, and in the Markdown filed beside them.
It reads Java, Kotlin and `.md` files under every source set,
and judges each by the package its directories spell.

```groovy
enforcePackageVocabulary {
    forbidWords under: 'example.framework',
        except: 'example.framework.kinds',
        words: ['ledger', 'sales order']
    allowWord word: 'ledger', inFile: 'src/main/java/example/framework/README.md'
    allowWords words: ['ledger', 'sales order'], inFile: 'src/main/java/example/framework/Rules.md'
}
```

- `forbidWords under:` names the closed root.
  `except:` takes one subtree or a list, on the same terms as in [package layering](#package-layering).
  `words:` takes one entry or a list.
- `allowWord` lets one project-relative file say one entry.
  `allowWords` takes one entry or a list, for a README that has to name every word its rule bans.
  An allowance naming a file the gate does not scan fails the build.

Text is parted into words at camel humps and at every non-letter.
An entry is parted the same way,
so `'SalesOrder'`, `'sales order'` and `'sales-order'` declare one phrase,
found in an identifier and in prose alike.
An entry with no letters is refused.
A phrase is found across a line break.
A plural is a different word, so a list forbidding `ledger` does not catch `ledgers`.

### Restricted calls

`enforceRestrictedCalls` lets only named types make a call under a package root.
It fits an ambient global read contained at one adapter,
while every other type takes what it found as an argument.

```groovy
enforceRestrictedCalls {
    restrictCall call: 'Example.getGlobalHandle()',
        under: 'example.framework',
        toTypes: ['Adapter'],
        instead: 'Seams.openGlobalHandle()'
}
```

- `call:` is matched as literal text in the code.
  Comments are stripped first; string literals are kept.
  A spelling with whitespace inside it does not match.
- `toTypes:` takes one type or a list, each named by the file it lives in.
  Left out, the root is closed to the call outright.
- `instead:` is optional and ends each finding,
  for a rule whose allowed seam lives in another build.

It reads Java and Kotlin under `src/`, every source set included.

### Referenced types

`enforceReferencedTypes` reads compiled classes, not source.
A class records types its source never spells:
an inferred local or a returned value puts a type into a descriptor, and the JVM resolves the descriptor.
It guards a library whose non-public names differ between builds,
and a runtime that refuses to load a namespace.

```groovy
enforceReferencedTypes {
    restrictReferences namespace: 'example.library',
        allowingPackages: ['example.library.api'],
        allowingTypes: ['example.library.internal.Seam']
    restrictReferences namespace: 'java.lang.reflect',
        inSourceSets: ['main'],
        reason: 'the runtime refuses to load it'
}
```

- `namespace:` is the restricted package root, declared once.
  A second declaration of it fails the build.
- `allowingPackages:` and `allowingTypes:` take one name or a list.
  A package allowance covers everything beneath it.
  A type allowance covers that type and the types nested in it.
  With neither, the namespace is closed outright.
- `inSourceSets:` takes one name or a list and limits the rule to those trees.
  A source set the build does not have fails the build.
- `reason:` ends each finding as the clause after "X references T, which".

A type allowance that nothing compiled names fails the build.
Package allowances are not judged this way.
Classes compiled into the namespace itself are exempt.
A finding names the class and the type, with no line number.
A class the gate cannot parse fails the build ahead of any finding;
the fix is a constant-pool tag added to the gate.

The gate depends on every compile task, Kotlin's included.

### Helper scripts

Gates are built from shared helpers.
Each script applies the helpers it uses itself,
since its suite applies it alone.
[lint-gate.gradle](gradle/tasks/lint/lint-gate.gradle) states the conventions every gate follows.

| Script | Provides |
| --- | --- |
| [tasks/source-text.gradle](gradle/tasks/source-text.gradle) | `jvmSourceIncludes` and `sourceEncoding`, shared by the gates and the formatter |
| [tasks/lint/lint-gate.gradle](gradle/tasks/lint/lint-gate.gradle) | `installLintGate`, `failOnViolations`, `declareScannedFiles`, `declareRuleSnapshot`, `readOneOrMany` |
| [tasks/lint/source-scan.gradle](gradle/tasks/lint/source-scan.gradle) | lines stripped of comments and literals, and the class and `@Nested` group each line sits in |
| [tasks/lint/package-roots.gradle](gradle/tasks/lint/package-roots.gradle) | package declarations, package roots, and layers with `except` |
| [tasks/lint/word-scan.gradle](gradle/tasks/lint/word-scan.gradle) | text parted into words, and where a declared phrase is said |
| [tasks/lint/markdown-scan.gradle](gradle/tasks/lint/markdown-scan.gradle) | the authored `.md` tree and the lines outside fenced blocks |
| [tasks/format/formatted-source-sets.gradle](gradle/tasks/format/formatted-source-sets.gradle) | the source sets less `formatterExcludedSourceSets` |
| [tasks/lint/formatting/formatted-source-line-gate.gradle](gradle/tasks/lint/formatting/formatted-source-line-gate.gradle) | the line-by-line body of the two formatting gates |
| [tasks/lint/naming/tests/double-naming-scan.gradle](gradle/tasks/lint/naming/tests/double-naming-scan.gradle) | a test double's holder, for the fake and mock gates |
| [tasks/diagnostics/cause-chain.gradle](gradle/tasks/diagnostics/cause-chain.gradle) | a throwable's cause chain, for the reports that name a real fault |
| [tasks/diagnostics/report-test-outcomes.gradle](gradle/tasks/diagnostics/report-test-outcomes.gradle) | `reportTestOutcomesOf`, the [test run report](#test-run-report) |

`installLintGate` gives a gate an output stamp, so Gradle can skip it, and makes `test` and `check` depend on it.

## AWT environment probe

`reportAwtEnvironment` reports whether the build machine can draw text:
the JVM, the headless setting, the JDK's font directory, the font families offered, and whether a string can be measured.
A JVM with no usable fonts fails the first Swing text component with an `InternalError` that names neither fonts nor the machine.
[The script](gradle/tasks/diagnostics/report-awt-environment.gradle) explains the failure.

Every consumer inherits the task, and nothing depends on it by default.
A consumer whose suites build text components declares what they need:

```groovy
awtFontFamiliesRequired = 1
```

The probe then runs before that project's `test` task,
and fails with the environment written out when the machine offers fewer families.
Run `gradlew reportAwtEnvironment` directly on any runner in doubt.

The probe runs in the build JVM, not the test JVM.
A test task on a different toolchain is told about the build's JVM.

## Formatting

The formatter rewrites source, so `java-conventions.gradle` does not apply it.
A repo carrying third-party code has source that is not its own to touch.
A repo opts in by applying the script itself:

```groovy
apply from: "${rootDir}/../Common-Java/gradle/spotless-java.gradle"
```

It has two passes, each runnable on its own, and a task running both:

- `spotlessApply` - `removeUnusedImports`, `trimTrailingWhitespace` and `endWithNewline`.
- `alignJavadocParams` - re-aligns the `@param` description column in Javadoc and KDoc blocks whose alignment has drifted.
  A block using one space throughout, or already aligned, is left as written.
  Run it after a rename.
- `formatJava` - both, in that order.

Neither pass lays code out:
no indentation, wrapping, brace or blank-line placement, and no reflowed comments.
Import order is left to Checkstyle.
[spotless-java.gradle](gradle/spotless-java.gradle) gives the reasons.

Nothing depends on these tasks, and `spotlessCheck` is detached from `check`,
so a build never fails on unclean source.

`alignJavadocParams` can be applied alone from [align-javadoc-params.gradle](gradle/tasks/format/align-javadoc-params.gradle).

### Excluded source sets

A repo that owns source it does not style, such as a mirror of a third party's API or generated code,
names those source sets in `formatterExcludedSourceSets`:

```groovy
ext.formatterExcludedSourceSets = ['bridgeStubs']
```

Both formatter passes and both [formatting gates](#formatting-gates) honour the list.
It may be set before or after applying the scripts,
and source sets declared after them are included.

## Reusable CI

[.github/workflows/_ci-gradle.yml](.github/workflows/_ci-gradle.yml) runs `./gradlew <tasks>` on a chosen runner.
Java and Kotlin both build through `gradlew`, so one workflow serves both.

```yaml
jobs:
  gradle:
    uses: Klark-Morrigan/Common-Java/.github/workflows/_ci-gradle.yml@master
    with:
      gradle-tasks: test jar
```

| Input | Type | Default | Purpose |
| --- | --- | --- | --- |
| `runner` | string, JSON label array | `''` | `runs-on` override, such as `'["self-hosted","some-label"]'` |
| `gradle-tasks` | string | `test jar` | Gradle tasks, separated by spaces |
| `checkout-path` | string | `.` | where the caller repo is checked out |
| `working-directory` | string | `.` | where `gradlew` runs |
| `sibling-checkouts` | string, JSON `[{repo, ref?, path}]` | `[]` | repos cloned beside the caller, so relative `apply from` paths resolve |
| `setup-java` | boolean | `false` | provision a Temurin JDK |
| `java-version` | string | `17` | the JDK `setup-java` provisions |
| `artifact-suffix` | string | `''` | tells this run's test-report artifact apart, for a caller invoking the workflow more than once |

- The optional secret `sibling_checkout_token` clones private siblings.
  Without it the clone uses `github.token`.
- The runner is the `runner` input, else the `CI_JAVA_RUNNER` variable, else `["ubuntu-latest"]`.
  `CI_JAVA_RUNNER` is a JSON label array read from the caller's repository.
- The workflow grants `github.token` `contents: read` only.
- A self-hosted runner's workspace is cleaned before checkout.
  The pinned actions need Node 24 on the runner.
- Gradle runs with `--no-daemon --console=plain --warning-mode all`.
- Test reports upload as `test-reports`, or `test-reports-<suffix>`, even when the build fails,
  and are kept for 7 days.

This repo's own [ci-gradle.yml](.github/workflows/ci-gradle.yml) calls the workflow to run `build` in `ci-smoke/`.
A manual run may override the runner.

## Local scripts and hooks

Run `./scripts/setup-hooks.sh` once per clone.
It points git at `.githooks/`.
The pre-commit hook then runs Common-Automation's shared fixes on staged files:
the executable bit, and trailing whitespace in Markdown and Gradle scripts.
It skips the fixes when Common-Automation is not checked out.

| Script | Does |
| --- | --- |
| `setup-hooks.sh` | wires `.githooks/` into git |
| `fix-permissions.sh` | Common-Automation's fix plus `gradlew` and `*/gradlew`, from [.github/lib/executable-paths.sh](.github/lib/executable-paths.sh) |
| `fix-whitespace.sh` | Common-Automation's trim plus `*.gradle`, from [.github/lib/trimmed-file-types.sh](.github/lib/trimmed-file-types.sh) |
| `run-ci-gradle.sh [tasks]` | the local mirror of `_ci-gradle.yml`, with the same Gradle flags |
| `run-ci-yaml-and-bash.sh` | the shell and YAML lint and the bats suites, as `ci-bash.yml` and `ci-yaml.yml` run them |
| `run-lint-yaml-and-bash.sh` | the lint half alone |
| `run-tests-bash.sh` | the bats half alone |
| `publish-version-tags.sh [vX.Y.Z]` | tags `origin/master` with `vX.Y.Z` and the floating `vX`; prompts when the version is omitted |

Each `.sh` has a `.bat` launcher for an Explorer double-click.
The shims find Common-Automation through `COMMON_AUTOMATION_ROOT`,
which defaults to the `Common-Automation` folder beside this repo.
They work on the repo `COMMON_JAVA_TARGET_REPO` names, which defaults to Common-Java.
`publish-version-tags.sh` always tags this repo.

`run-ci-gradle.sh` is Common-Java's own engine.
A consumer repo calls it with `COMMON_JAVA_TARGET_REPO` set,
and must then pass the tasks its CI runs.
Without a target, it runs in `ci-smoke/` and defaults to `build`.

A consumer JVM repo's pre-commit hook reads `executable-paths.sh` and `trimmed-file-types.sh`
from `../Common-Java/.github/lib/`,
so the tier's executables and text types are declared once.

## Testing the scripts

[ci-smoke/](ci-smoke/) applies `java-conventions.gradle` by relative path, as a consumer does.
Its `testGradle` task runs a TestKit suite per script:
each suite applies its one script to a throwaway project and drives a real build.
A suite and its `.txt` fixtures sit at the script's own path under `ci-smoke/src/test-gradle/tasks/`.

Two checks guard the suites themselves:

- `verifyEveryGateHasASuite` fails when a script that registers a task has no suite at its path.
- `verifyConventionsWireEveryGate` fails when `java-conventions.gradle` leaves a lint gate out of `check`.

Both run with `check`, as does `testGradle`.

## Consuming with a domain layer

A project that needs more than the generic conventions,
such as extra classpath, a version source or a custom artifact location,
puts that in its own intermediate convention script.
That script applies `java-conventions.gradle` first, then adds its layer.
Consumers apply the intermediate script.
The dependency arrow only points up to this repo:
nothing here knows what those layers are.
