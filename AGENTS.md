# AGENTS.md

Guidance for AI coding agents working in the ServiceTalk repository.
Humans should start with `README.adoc` and `CONTRIBUTING.adoc`.

## What this repo is

ServiceTalk is a JVM networking framework built on Netty, providing HTTP/1.1,
HTTP/2, and gRPC clients and servers over an asynchronous `Publisher`/`Single`/
`Completable` core, with blocking and streaming programming models layered on
top.

It is a Gradle multi-project build of ~90 modules named
`servicetalk-<area>[-<impl>]`:

- `servicetalk-<area>-api` — public interfaces and types (e.g. `http-api`).
- `servicetalk-<area>-netty` — the Netty-backed implementation (e.g. `http-netty`).
- `*-internal` — not part of the public API. Breaking changes are allowed.
- `servicetalk-examples` — runnable sample applications, one Gradle subproject
  per example (`servicetalk-examples:http:helloworld`, and so on).

When changing behavior, check whether the change belongs in the `-api` module
(contract) or the `-netty` module (implementation).

## Build and verify

```shell
./gradlew build                  # compile, test, and run all quality gates
./gradlew test                   # tests only, all modules
./gradlew :servicetalk-http-api:test                     # one module
./gradlew :servicetalk-http-api:test --tests "io.servicetalk.http.api.HeaderUtilsTest"
./gradlew quality                # checkstyle, PMD, SpotBugs, javadoc, dependency analysis
```

Run `quality` before you claim a change is finished. It fails on things `test`
does not catch.

## Constraints that break the build

| Constraint | Where it is enforced |
|---|---|
| Java 8 bytecode by default, **including test code** (`options.release = 8`): no `var`, `List.of`, `Map.of`, text blocks, or other Java 9+ APIs. A minority of modules opt into a higher level — the `jersey3-*`, `jersey4-*` and `*jakarta*` variants, `servicetalk-concurrent-jdkflow`, and `servicetalk-data-jackson3`. Check the module's `build.gradle` for `sourceCompatibility`. | `servicetalk-gradle-plugin-internal/src/main/groovy/io/servicetalk/gradle/plugin/internal/ServiceTalkLibraryPlugin.groovy` |
| Every new file needs the Apache 2.0 header (see below). | Checkstyle `RegexpHeader` |
| Lines wrap at 120 characters. | Checkstyle `LineLength` |
| Imports ordered: `io.servicetalk` → third-party → `java.*` → static, alphabetical within each group. | Checkstyle `CustomImportOrder` |
| No `System.out`/`System.err` in `src/main`. | Checkstyle `ConsolePrint` |
| Every `package-info.java` carries `@ElementsAreNonnullByDefault`. | Checkstyle |
| An unused or undeclared dependency fails the build. Adding an import means adding the matching Gradle dependency. | `dependency-analysis` plugin, root `build.gradle` |

CI compiles and runs tests on JDK 8, 11, 17, 21, and 25.

### License header for a new file

```java
/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
```

Use the current year for a new file. When editing an existing file, extend its
existing year list — `2019` becomes `2019, 2026`, and `2019-2025` becomes
`2019-2026`.

## Skills

Detailed, task-specific guidance lives in `.agents/skills/`. Read the relevant
`SKILL.md` **before** you start that kind of task, not after something fails.

| Skill | Read it when |
|---|---|
| `.agents/skills/servicetalk-testing/SKILL.md` | Adding or changing any test, or diagnosing a failing or hanging test. |
| `.agents/skills/servicetalk-commit-messages/SKILL.md` | Writing a commit message, or a pull request title and description. |

Some modules also carry their own `AGENTS.md` with guidance specific to that
module. When one exists next to the code you are editing, it applies in
addition to this file.

## Testing

All tests live in `src/test/java` and end in `Test.java`. There is no separate
integration-test source set, task, or tag — a test that binds a real socket
sits beside one that does not.

**Before you add or change any test, read
`.agents/skills/servicetalk-testing/SKILL.md`.** The conventions here are not
the JUnit defaults, and guessing produces code that fails review.

## Contributing

**Before you write a commit message or a pull request description, read
`.agents/skills/servicetalk-commit-messages/SKILL.md`.** Pull requests are
squash-merged, so the pull request description becomes the commit message.

`CONTRIBUTING.adoc` covers the project's communication standards. Follow it
for anything you write for a human to read.
