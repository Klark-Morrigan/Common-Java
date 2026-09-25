# Common-Java

Shared, framework-agnostic build/CI/tooling scaffolding for JVM projects.
This repo holds no runtime library code and no knowledge of any specific
consumer - it is the generic tier that downstream projects build on.

## Index

- [What lives here](#what-lives-here)
- [Gradle conventions](#gradle-conventions)
- [Lint gates](#lint-gates)
- [AWT environment probe](#awt-environment-probe)
- [Formatting](#formatting)
- [Reusable CI](#reusable-ci)
- [Consuming with a domain layer](#consuming-with-a-domain-layer)

## What lives here

- `gradle/java-conventions.gradle` - generic JVM build conventions.
- `gradle/tasks/lint/` - source-text gates the conventions apply.
- `gradle/tasks/diagnostics/` - what the conventions report about the machine
  rather than the code.
- `gradle/spotless-java.gradle`, `gradle/tasks/format/` - the opt-in,
  on-demand source cleanup. Mechanical fixes only; never re-lays code.
- `.github/workflows/_ci-gradle.yml` - reusable Gradle build/test workflow.
- `.github/workflows/ci-bash.yml`, `ci-yaml.yml` - thin callers that
  delegate shell/YAML linting to Common-Automation.
- `.github/lib/trimmed-file-types.sh` - the JVM text types every JVM repo's
  pre-commit hook adds to Common-Automation's whitespace trim.
- `scripts/` - shims to Common-Automation's lint/test/permission/whitespace
  engines.

## Gradle conventions

`gradle/java-conventions.gradle` standardises the language target (Java 17),
the test stack (JUnit 5 + AssertJ + Mockito),
and JaCoCo coverage (plus a `coverage` task) -
over the main sources and,
for a project applying `java-test-fixtures`,
the fixtures it publishes too.
A consumer applies it by path:

```groovy
apply from: "${rootDir}/../Common-Java/gradle/java-conventions.gradle"
```

Applied by path rather than published as a plugin, on the assumption that
consumers are checked out as siblings under the same parent directory, so
a relative path is the lowest-ceremony single source of truth. No
`settings.gradle` change is needed.

Every test run ends with the ten slowest tests,
so a creeping suite runtime is visible rather than hidden in the aggregate,
and a failing one adds the failures twice over:
a clean list of names,
then the same names under the same numbers with the stack trace behind each,
cause chain followed to its end.
Both sit at the bottom of the log,
where a reader starts,
rather than where each test happened to run.
Details stop after the first ten failures -
a run that dies on its environment fails every test it has with one fault -
and the rest are in the `test-reports` artifact.

A `Test` task registered beside `test`,
such as a TestKit tree,
asks for the same report:

```groovy
reportTestOutcomesOf(it)
```

Checkstyle runs from `gradle/checkstyle.xml`, whose import-order rule takes
the blocks as a property rather than naming any consumer's packages. The
default is everything-then-the-JDK; a consumer (or a layer above it, such
as a family's own conventions script) states its real blocks over the top:

```groovy
def importGroups = 'com.example.api,mylib,myapp,/^org\\./,java'

checkstyle.configProperties.importGroups = importGroups
checkstyle.configProperties.staticImportGroups = importGroups
```

Every group a project imports from has to be named. Checkstyle sorts an
import matching no group after every named one - below `java.*` - so a
package left off the list fails in a way that reads as nothing to do with
the list.

## Lint gates

`java-conventions.gradle` also applies the gates under
[gradle/tasks/lint/](gradle/tasks/lint/), so a consumer inherits them from
one place alongside checkstyle. Each asserts one convention no compiler
reports, and each runs as part of `check` and `test`. All but one read
source text; `enforceReferencedTypes` reads the classes that text compiled
to, for the reason given below:

| Task | Rule |
| --- | --- |
| `enforceCamelCaseTestNames` | test methods are named in camelCase, not snake_case |
| `enforceDocLinksResolve` | a relative link in a `.md` file points at something that exists |
| `enforceIdCasing` | prose spells the abbreviation `ID`, not `id` |
| `enforceMethodsOrderedByVisibility` | production methods run most-public-first |
| `enforceNoMagicLiteralsKotlin` | Kotlin numbers are named; Java's ride in `checkstyle.xml` |
| `enforceNoTestVocabulary` | production code is not written in the words of the suites that test it |
| `enforceNoTrailingWhitespace` | no line ends in whitespace |
| `enforcePackageLayering` | a package root does not import one the consumer declared it closed to |
| `enforcePackageVocabulary` | a package root does not say a word the consumer declared it closed to |
| `enforceReferencedTypes` | compiled code names a type from an unstable namespace only where the consumer declared that type stable |
| `enforceRestrictedCalls` | a call a package root is closed to is made only in the types the consumer named |
| `enforceSingleBlankLines` | at most one consecutive blank line |
| `enforceSuffixOnFakes` | hand-written test doubles are suffixed `Fake` |
| `enforceSuffixOnMocks` | Mockito mock variables are suffixed `Mock` |
| `enforceTestsNested` | every `@Test` sits inside a `@Nested` class |

A gate only ever reports, which is what makes inheriting them everywhere
free. Each carries its own TestKit integration test in `ci-smoke/` that
applies the one script into a throwaway project and drives a real build,
and `verifyEveryGateHasASuite` counts the scripts against those suites: a
gate whose suite was never written, or whose folder was renamed out from
under the tree that collects them, is a gate nothing runs - which is
exactly what a passing build looks like.

Neither half of being a gate is written out per script. `lint-gate.gradle`
owns both: the stamp that lets Gradle skip a gate whose sources have not
changed, and the `test`/`check` dependency that makes a build run it at
all. A gate takes them in one call, `installLintGate(taskProvider)`, so
there is no second thing for the next gate's author to remember - and
forgetting either was silent in both directions.

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
    forbidImport under: 'example.framework', of: 'example.feature'
    forbidImport under: 'example.framework.reads',
                    of: ['example.framework.writes',
                        'example.framework.layout']
}
```

`of` takes one package root or a list of them, since a root is usually
closed to several at once - the siblings it must not reach into. Each
entry is still its own edge; the list only spares the closed side from
being restated once per pair.

Every source set is scanned, not `main` and `test` alone: a layering rule
that exempts a tree is a layering rule with a hole in it. Test sources
matter as much as production ones, since a suite reaching across the line
for a real type is the shortest way to make it compile.

`enforcePackageVocabulary` is that gate one level in: it keeps a package
root from being *written in* another's words, in code, in comments, and in
the markdown filed beside them. A leaked import stops compiling the day
the package moves; a leaked noun in a Javadoc survives every move in
silence, and it is what a reader of the package actually reads. Like the
layering rule, the vocabulary is a per-project fact this repo cannot know:

```groovy
enforcePackageVocabulary {
    forbidWords under: 'example.framework',
                words: ['feature', 'features', 'territory']
    allowWord word: 'territory',
            inFile: 'src/main/java/example/framework/theme/README.md'
    allowWords words: ['feature', 'features'],
            inFile: 'src/utils/java/example/framework/ui/Viewer.java'
}
```

`allowWords` is the same allowance for several words in one file - the
shape a README explaining a rule takes, since it has to name every word
the rule bans. `enforceNoTestVocabulary` takes both spellings too.

Words are matched whole and parted at camel humps, so `blocks` is its own
word and `clipInsideTheNationalBorder` says `national border`. A plural is
a different word, so a list forbidding `bloc` does not catch `blocs` - the
consumer declares both. A phrase is found across a line break too, since
prose wraps where the column runs out rather than where the phrase ends.

The escape hatch is a declared allowance rather than an inline marker
comment, and the declaration is the point: an entry is a line someone adds
to the build and a reviewer reads in the diff, where an inline opt-out is
invisible until somebody greps for it. An allowance is per file and per
word, and one naming a file the gate does not scan fails the build - a
stale path exempts nothing while reading as though it does.

`enforceRestrictedCalls` is the third of the family, one step finer than
either: it says which *types* may make a particular call. That is the rule
an architecture needs when one API is legitimate at a single seam and a
defect everywhere else - an ambient global read contained at the adapter
that hands what it found downward, while every other type takes it as an
argument:

```groovy
enforceRestrictedCalls {
    restrictCall call: 'Example.getGlobalHandle()',
            under: 'example.framework',
            toTypes: ['Adapter']
}
```

Stated over where a call may be made rather than over how many there are:
a count pins whichever number the code was standing on, where a named list
fails the build for a caller nobody declared. Omitting `toTypes` closes
the root to the call outright, so a mistyped key enforces the rule
everywhere rather than allowing it everywhere.

A type is named by the file it lives in. The call is matched as literal
text against the code with comments stripped first, so a Javadoc
explaining why a call is contained is prose about the rule rather than a
breach of it.

`enforceReferencedTypes` is the fourth, and the only gate that reads
compiled classes. It asks which of a namespace's type names the build is
allowed to carry, for a library whose published surface is stable and
whose internals are not - one shipped per platform, a shaded
redistribution, an obfuscated release whose non-public names are
regenerated. Code compiled against one spelling then fails to link against
another, and it fails at the call rather than at load:

```groovy
enforceReferencedTypes {
    restrictReferences namespace: 'example.library',
        allowingPackages: ['example.library.api'],
        allowingTypes: ['example.library.internal.Seam']
}
```

It reads classes because a source pass cannot see the reference that
matters. A file that never names a type still compiles one into a
descriptor wherever an inferred local takes a returned value, or the
compiler writes a bridge for an inherited method - and the descriptor is
what the runtime resolves. Both cases are real: one shipped a crash, the
other sits in a consumer that extends a library base class and names
nothing.

A package allowance covers everything beneath it; a type allowance covers
that type and the types nested inside it, which are declared with it and
renamed with it. A sibling in the same package is its own declaration.
Omitting both closes the namespace outright, so a mistyped key closes it
rather than opening it.

The check runs both ways. A type allowance that nothing compiled names
fails the build too: it reads as a dependency the project has, and whoever
writes the next reference to that type finds it already approved for
reasons nobody checked. That is a failure rather than a warning for the
reason the whole family states its rules as named lists - an entry is a
line a reviewer reads in the diff, and a gate skipped while its inputs
hold would say a warning once and then never again. Package allowances are
not judged this way, being a policy about a whole tree rather than a claim
about what is used.

Classes compiled into the namespace itself are passed over: a class filed
under a library's own package is written to that library's internals by
definition, and it is the one place the promise does not apply. A
violation names the class and the type it referenced, with no line
number - a class file records which types a method touches, not where
each was written.

A class the walk cannot follow - one carrying a constant-pool tag from a
class-file version newer than the walk knows - fails the build ahead of
any of that, and says which classes and how many. The cause is the gate
falling behind rather than anything in the code, and the fix is a tag
added to its table; but a gate that cannot read a class cannot promise
what it exists to promise, and a class read as empty is indistinguishable
from a class that passed.

`enforceIdCasing` keeps `ID` spelt as English rather than as a field name.
The two spellings are not a style toss-up: `ID` abbreviates
*identification*, while `id` is how a symbol is spelt. Prose drifts to the
lower-case form on its own, because a sentence written beside
`market.getId()` borrows the casing of the symbol it describes - and left
alone the drift wins on volume.

Unlike the vocabulary gates it owns its rule outright, since the correct
spelling of an abbreviation is not a per-project fact. A consumer declares
only what to leave alone:

```groovy
enforceIdCasing {
    exemptPath glob: 'docs/vendor/**/*.md'
}
```

Nearly all of the gate is spent *not* firing, which is what makes a rule
about a two-letter word usable at all. A backticked `` `id` ``, a
`{@code}` span, a `<pre>` sample and a fenced block are source being
quoted and keep the casing the source has; the token after `@param` is a
parameter's name; `mod-id`, `market.id` and `field_id` are file, path and
column spellings. In code only comment text is read, so no identifier,
literal or CSV column can ever be reported - the gate cannot ask for a
rename, only for a word to be written properly or backticked.

Markdown is scanned across the whole project rather than under `src/`,
because a repository's longest prose is its root README. An `exemptPath`
matching no file fails the build, on the same reasoning as a stranded
allowance above.

## AWT environment probe

`gradle/tasks/diagnostics/report-awt-environment.gradle` reports whether the
machine a build is running on can draw text at all:
the JVM that answered,
the headless setting,
the JDK's own font directory,
how many font families are offered,
and whether a string can actually be measured.

It exists because of how badly that failure reads without it.
A JVM with no usable fonts cannot construct a font manager,
and the first Swing text component built in one dies with
`java.lang.InternalError: java.lang.reflect.InvocationTargetException` -
a JDK internal with an empty cause,
naming neither fonts nor the machine,
several tests into a suite about something else entirely.
The fix is a package on the runner,
so the machine is the one thing the failure has to name.

Every consumer inherits the task and nothing is gated by default,
since a repo with no UI code does not care what its runner can draw.
A consumer whose suites build text components declares what it needs:

```groovy
awtFontFamiliesRequired = 1
```

From then on the probe runs ahead of that project's tests
and fails with the environment written out,
rather than letting the suites fail one at a time.
Run it directly - `gradlew reportAwtEnvironment` - on any runner whose graphics
stack is in doubt.

One limitation worth stating:
the probe runs in the build JVM while the suites run in the test JVM.
Fonts are installed per machine rather than per process,
so the answer carries across in practice,
but a project whose test task uses a different toolchain is being told about the
build's JVM rather than its own.

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

- `spotlessApply` - mechanical fixes with exactly one correct answer:
  `removeUnusedImports`, `trimTrailingWhitespace`, `endWithNewline`.
- `alignJavadocParams` - re-aligns the `@param` description column in
  Javadoc and KDoc blocks whose alignment has drifted. It fixes drift
  rather than imposing a style: a block that uses one space throughout, or
  whose descriptions already agree, is left exactly as written. This is
  the pass to reach for after a rename.
- `formatJava` - both, in that order.

**Neither pass lays code out.**
No indentation, no line wrapping, no brace or blank-line placement, no reflowing of comment prose.
A whole-file formatter - Spotless driving the Eclipse JDT formatter from an `eclipse-formatter.xml` -
used to sit here and was removed:
its settings could not express this family's continuation indent and argument wrapping,
so every run re-laid code it had no business touching,
and a single invocation rewrote several hundred files and buried the real change among them.
Code layout belongs to the person writing it,
backed by the reporting gates under `gradle/tasks/lint/`, which never rewrite.

Import *order* is deliberately absent for a related reason:
the group list is assigned per repo over the default in `java-conventions.gradle`,
and checkstyle's `ImportOrder` already reports a wrong order,
so a second copy of the list here would be one more thing to drift out of true.

Nothing depends on any of them, and `spotlessCheck` is detached from
`check` (`enforceCheck = false`), so a build cannot fail on merely
unclean source.

A repo that owns source it does not style - a mirror of a third party's
shape, generated code - names those source sets first, and both passes
honour the list:

```groovy
ext.formatterExcludedSourceSets = ['bridgeStubs']
apply from: "${rootDir}/../Common-Java/gradle/spotless-java.gradle"
```

Both passes read source sets,
and so does the `enforceNoTrailingWhitespace` gate,
which leaves `.gradle` scripts owned by neither -
and this repo has no Gradle project at its root to format them with anyway.
So Gradle scripts are trimmed on commit instead:
[.github/lib/trimmed-file-types.sh](.github/lib/trimmed-file-types.sh) declares
`*.gradle` as this tier's text type,
and every JVM repo's pre-commit hook hands that file to the shared hook body
in Common-Automation,
which owns the trim itself and covers Markdown on its own.
Declared once here rather than per repo,
so a second type is one edit rather than five.

Installed per clone with `./scripts/setup-hooks.sh`;
`./scripts/fix-whitespace.sh` trims a whole repo,
which is how files that predate the hook get healed.
A consuming JVM repo's own `scripts/fix-whitespace.sh` points that at itself,
so each mod trims its own Gradle scripts without restating the type.

## Reusable CI

`.github/workflows/_ci-gradle.yml` runs `./gradlew <tasks>` on a chosen
runner. It is language-agnostic (Java and Kotlin both build through
`gradlew`, so there is no separate ci-java / ci-kotlin), with all variance
expressed as inputs: `runner`, `gradle-tasks`, `setup-java`,
`java-version`, `artifact-suffix`.

Every run uploads its Gradle HTML test reports as a `test-reports`
artifact, so the per-test detail behind a red check is reachable without
access to the runner's disk. A caller that invokes the engine more than
once in a workflow (a matrix over build variants) passes `artifact-suffix`
to keep the names apart.

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
