# Common-Java

Shared, framework-agnostic build/CI/tooling scaffolding for JVM projects.
This repo holds no runtime library code and no knowledge of any specific
consumer - it is the generic tier that downstream projects build on.

## Index

- [What lives here](#what-lives-here)
- [Gradle conventions](#gradle-conventions)
- [Lint gates](#lint-gates)
- [Formatting](#formatting)
- [Reusable CI](#reusable-ci)
- [Consuming with a domain layer](#consuming-with-a-domain-layer)

## What lives here

- `gradle/java-conventions.gradle` - generic JVM build conventions.
- `gradle/tasks/lint/` - source-text gates the conventions apply.
- `gradle/spotless-java.gradle`, `gradle/tasks/format/` - the opt-in,
  on-demand formatter.
- `.github/workflows/_ci-gradle.yml` - reusable Gradle build/test workflow.
- `.github/workflows/ci-bash.yml`, `ci-yaml.yml` - thin callers that
  delegate shell/YAML linting to Common-Automation.
- `scripts/` - shims to Common-Automation's lint/test/permission engines.

## Gradle conventions

`gradle/java-conventions.gradle` standardises the language target (Java
17), the test stack (JUnit 5 + AssertJ + Mockito), and JaCoCo coverage
(plus a `coverage` task). A consumer applies it by path:

```groovy
apply from: "${rootDir}/../Common-Java/gradle/java-conventions.gradle"
```

Applied by path rather than published as a plugin, on the assumption that
consumers are checked out as siblings under the same parent directory, so
a relative path is the lowest-ceremony single source of truth. No
`settings.gradle` change is needed.

## Lint gates

`java-conventions.gradle` also applies the gates under
[gradle/tasks/lint/](gradle/tasks/lint/), so a consumer inherits them from
one place alongside checkstyle. Each is a source-text pass asserting one
convention no compiler can see, and each runs as part of `check` and
`test`:

| Task | Rule |
| --- | --- |
| `enforceDocLinksResolve` | a relative link in a `.md` file points at something that exists |
| `enforceMethodsOrderedByVisibility` | production methods run most-public-first |
| `enforceNoMagicLiteralsKotlin` | Kotlin numbers are named; Java's ride in `checkstyle.xml` |
| `enforcePackageLayering` | a package root does not import one the consumer declared it closed to |
| `enforceSingleBlankLines` | at most one consecutive blank line |
| `enforceSuffixOnFakes` | hand-written test doubles are suffixed `Fake` |
| `enforceSuffixOnMocks` | Mockito mock variables are suffixed `Mock` |
| `enforceTestsNested` | every `@Test` sits inside a `@Nested` class |

A gate only ever reports, which is what makes inheriting them everywhere
free. Each carries its own TestKit integration test in `ci-smoke/` that
applies the one script into a throwaway project and drives a real build.

`enforceDocLinksResolve` skips version-control and build-tool directories
by name, which is all this repo knows about. A consumer whose own tooling
writes markdown names that directory too, from a script that runs after
the conventions - the list is read when the tree is scanned, so a late
addition still counts:

```groovy
docLinkScanExcludedDirectoryNames << 'graphify-out'
```

`enforcePackageLayering` checks nothing until the consumer says what its
layers are, since which package may not reach which is a per-project fact
this repo cannot know. Each edge is declared whole, in one call, so a
half-stated rule is never a thing that exists:

```groovy
enforcePackageLayering {
    forbidImport under: 'kmu.maplayers.base', of: 'kmu.maplayers.politicalmap'
}
```

Every source set is scanned, not `main` and `test` alone: a layering rule
that exempts a tree is a layering rule with a hole in it. Test sources
matter as much as production ones, since a suite reaching across the line
for a real type is the shortest way to make it compile.

## Formatting

The formatter is opt-in and never automatic, which is why
`java-conventions.gradle` does not apply it: it rewrites source, and most
consumers must never have it - the Starsector port mods carry third-party
code where a reformat destroys the diff against upstream. A repo opts in
by applying the script itself:

```groovy
apply from: "${rootDir}/../Common-Java/gradle/spotless-java.gradle"
```

It comes in two passes, each independently runnable:

- `spotlessApply` - Spotless driving the Eclipse JDT formatter from
  [gradle/eclipse-formatter.xml](gradle/eclipse-formatter.xml). Owns code
  layout and leaves doc comments alone.
- `alignJavadocParams` - re-aligns the `@param` description column in
  Javadoc and KDoc blocks whose alignment has drifted. It fixes drift
  rather than imposing a style: a block that uses one space throughout, or
  whose descriptions already agree, is left exactly as written. This is
  the pass to reach for after a rename, where a full Spotless run would
  bury the change in unrelated normalisation.
- `formatJava` - both, in that order.

Nothing depends on any of them, and `spotlessCheck` is detached from
`check` (`enforceCheck = false`), so a build cannot fail on merely
unformatted code. That is a requirement rather than caution: the JDT
formatter normalises a few constructs it has no setting to preserve, so a
run is read as a diff and curated by hand.

A repo that owns source it does not style - a mirror of a third party's
shape, generated code - names those source sets first, and both passes
honour the list:

```groovy
ext.formatterExcludedSourceSets = ['bridgeStubs']
apply from: "${rootDir}/../Common-Java/gradle/spotless-java.gradle"
```

## Reusable CI

`.github/workflows/_ci-gradle.yml` runs `./gradlew <tasks>` on a chosen
runner. It is language-agnostic (Java and Kotlin both build through
`gradlew`, so there is no separate ci-java / ci-kotlin), with all variance
expressed as inputs: `runner`, `gradle-tasks`, `setup-java`,
`java-version`.

```yaml
jobs:
  gradle:
    uses: Klark-Morrigan/Common-Java/.github/workflows/_ci-gradle.yml@master
    with:
      gradle-tasks: test jar
```

## Consuming with a domain layer

A project that needs more than the generic conventions (extra classpath,
a version source, a custom artifact location) puts that in its own
intermediate convention script, which applies `java-conventions` first and
then adds its layer. Consumers then apply the intermediate script. The
dependency arrow only ever points up to this repo - nothing here reaches
back down to know what those layers are.
