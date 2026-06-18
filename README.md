# Common-Java

Shared, framework-agnostic build/CI/tooling scaffolding for JVM projects.
This repo holds no runtime library code and no knowledge of any specific
consumer - it is the generic tier that downstream projects build on.

## Index

- [What lives here](#what-lives-here)
- [Gradle conventions](#gradle-conventions)
- [Reusable CI](#reusable-ci)
- [Consuming with a domain layer](#consuming-with-a-domain-layer)

## What lives here

- `gradle/java-conventions.gradle` - generic JVM build conventions.
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

## Reusable CI

`.github/workflows/_ci-gradle.yml` runs `./gradlew <tasks>` on a chosen
runner. It is language-agnostic (Java and Kotlin both build through
`gradlew`, so there is no separate ci-java / ci-kotlin), with all variance
expressed as inputs: `runs-on`, `gradle-tasks`, `setup-java`,
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
