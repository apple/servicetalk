---
name: servicetalk-testing
description: >-
  How tests are written in the ServiceTalk repository. Read before adding or
  changing any test, or when diagnosing a failing, flaky, or hanging test.
  Covers assertion style, naming, the reactive and transport test harnesses,
  test doubles, and what to verify before claiming a change is done.
---

# Writing tests in ServiceTalk

ServiceTalk is a JVM networking framework (HTTP/1.1, HTTP/2, gRPC) built on
Netty over an asynchronous `Publisher`/`Single`/`Completable` core. Its test
conventions are **not** the JUnit defaults. Follow this file rather than
general Java testing habits.

All tests live in `src/test/java` and end in `Test.java`. There is no separate
integration-test source set, Gradle task, or `@Tag` — a test that binds a real
socket sits beside one that drives an `EmbeddedChannel`. The only real choice
is which harness you need. See "Pick your harness" below.

## 1. Rules for every test

1. **Java 8 only.** Test code compiles with `--release 8`. No `var`, no
   `List.of`/`Map.of`/`Set.of`, no text blocks, no `Optional.isEmpty()`. Use
   `Arrays.asList(...)`, `Collections.singletonList(...)`, and explicit types.
2. **Apache 2.0 header** at the top of every new file, with the current year.
   See `AGENTS.md` for the exact block.
3. **Name the class `<ClassUnderTest>Test`**, package-private, in the same
   package as the class under test.
4. **Name methods `<methodUnderTest><Condition><ExpectedResult>`** in camelCase.
   No `should` prefix, no underscores. Real examples from the repo:
   `decodeThrowsIfMoreThanMaxBytes`, `cancelBeforeSetDoneClearsInterrupt`,
   `timeoutExceptionDeliveredBeforeUpstreamException`.
5. **Assert with Hamcrest** — `assertThat(actual, matcher)`. 538 files use
   Hamcrest; 4 use AssertJ. Do not add AssertJ.
6. **Test exceptions with `assertThrows`**, never try/catch plus `fail()`.
7. **Do not add `@Timeout`.** A global default already applies to every test:
   10s locally, 30s in CI
   (`ServiceTalkLibraryPlugin.groovy`, `junit.jupiter.execution.timeout.default`).
   Add one only for a deliberately long stress loop.
8. **Do not use `@Nested` or `@DisplayName`.** Tests here are flat. There are
   zero `@DisplayName` uses in the repo.
9. **Never delete or disable a test to make a build pass.** Fix it.

## 2. Assertions: bad → good

Put the subject in `assertThat` and let the matcher describe the expectation.
A good matcher prints what it actually got when it fails; `assertTrue` prints
`expected: <true> but was: <false>`.

| Avoid | Use |
|---|---|
| `assertTrue(list.isEmpty())` | `assertThat(list, is(empty()))` |
| `assertEquals(n, list.size())` | `assertThat(list, hasSize(n))` |
| `assertTrue(list.contains(x))` | `assertThat(list, hasItem(x))` |
| `assertTrue(x instanceof Foo)` | `assertThat(x, instanceOf(Foo.class))` |
| `assertEquals(expected, actual)` | `assertThat(actual, is(expected))` |
| `assertNull(x)` | `assertThat(x, is(nullValue()))` |
| `assertTrue(a == b)` | `assertThat(a, is(sameInstance(b)))` |
| `assertTrue(s.startsWith("x"))` | `assertThat(s, startsWith("x"))` |
| `assertTrue(map.containsKey(k))` | `assertThat(map, hasKey(k))` |

Standard import block:

```java
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
```

`assertTrue`, `assertFalse`, `assertNotNull`, and `assertThrows` from
`org.junit.jupiter.api.Assertions` are fine for plain boolean and exception
checks. Reach for Hamcrest whenever a matcher would produce a better failure
message.

**For an expected failure, use `DELIBERATE_EXCEPTION`**, not
`new RuntimeException("boom")`. It is used in 250 test files and stubs out
`fillInStackTrace()`, so it is cheap and produces stable output:

```java
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;

publisher.onError(DELIBERATE_EXCEPTION);
assertThat(subscriber.awaitOnError(), is(sameInstance(DELIBERATE_EXCEPTION)));
```

To inspect a thrown exception, capture it and chain:

```java
ExecutionException e = assertThrows(ExecutionException.class, () -> single.toFuture().get());
assertThat(e.getCause(), instanceOf(MaxMessageSizeExceededException.class));
```

## 3. Every test must be able to fail

Each test must end on a real assertion — `assertThat`, `assertThrows`,
`verify`, or an `await*` that throws on the wrong signal.

**Never call a `boolean`-returning helper and discard the result.** This is a
live bug in the repo: `DefaultHttpSetCookiesTest:765` calls
`areSetCookiesEqual(...)` bare while every other call site in the same file
wraps it in `assertTrue(...)`. That test passes no matter what the cookies
contain. Write helpers that assert internally and return `void`.

If a test's only contract really is "this does not throw", say so in the name
(`...DoesNotThrow`) so the next reader knows it is deliberate.

## 4. Test classes run concurrently

`junit-platform.properties` (shipped in `servicetalk-test-resources`, which is
on the test classpath of 56 modules) sets:

```
junit.jupiter.execution.parallel.enabled = true
junit.jupiter.execution.parallel.mode.default = same_thread
junit.jupiter.execution.parallel.mode.classes.default = same_thread
```

Methods within one class run on one thread, but **separate test classes run at
the same time**. Therefore:

- Never depend on shared static mutable state, and be careful with
  process-global switches such as `AsyncContext.disable()`.
- Bind servers with `localAddress(0)` so the OS picks a free port. Never
  hardcode one.
- **Close everything you open.** An unclosed `ServerContext` or `IoExecutor`
  does not fail your test — it leaks a socket or a thread pool into a test
  class running beside yours, which then looks flaky for no reason.
- **Clear a thread interrupt flag in `finally`.** Pooled threads carry it into
  unrelated tests:

```java
} finally {
    // A leaked interrupt would otherwise bleed into the next test on this thread.
    Thread.interrupted();
}
```

## 5. Pick your harness

| What you are testing | Harness | Read |
|---|---|---|
| A `Publisher`/`Single`/`Completable` operator | `TestPublisher` + `TestPublisherSubscriber` | [references/reactive-sources.md](references/reactive-sources.md), then `TimeoutPublisherTest` |
| A codec or Netty `ChannelHandler` | `EmbeddedChannel` — no sockets | [references/transport-and-netty.md](references/transport-and-netty.md), then `HttpRequestEncoderTest` |
| A filter, client, or server end to end | Real bound server plus a real client | [references/transport-and-netty.md](references/transport-and-netty.md), then `BlockingStreamingInputStreamTest` |
| A plain value type or utility | A flat JUnit test, no harness | `RoundRobinSelectorTest` |

Reading the named exemplar is faster and more accurate than reading prose.
Do that before writing.

## 6. Waiting for asynchronous results

`.toFuture().get()` with **no** timeout is the norm — 804 of 817 call sites.
That is deliberate: the global test timeout fires, and
`TimeoutTracingInfoExtension` (registered automatically for every test) dumps
every thread's stack just before JUnit interrupts the test. Do not add timed
overloads to match a different codebase's style.

**Never sleep and then assert** on an asynchronous side effect. Poll a real
signal instead — a `CountDownLatch`, a `BlockingQueue`, or an observer. Several
recent commits exist only to make this change, for example
"opentelemetry-http: await span export in filter tests instead of sleeping" and
"Deflake processor GC tests".

For errors raised on a thread other than the test thread, collect them and
assert at the end:

```java
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;

Queue<Throwable> errors = new LinkedBlockingQueue<>();
// ... callbacks add to errors ...
assertNoAsyncErrors(errors);
```

## 7. Test doubles

Both Mockito styles are in active use — static `mock(Foo.class)` alone in 176
files, `@Mock` with `@ExtendWith(MockitoExtension.class)` in 26. Either is
acceptable. Use static `mock()` for a one-off, `@Mock` when several tests share
the field.

- **Mock `Subscriber` and `Subscription` interfaces** when you only need to
  assert which callbacks fired: `mock(SingleSource.Subscriber.class)` and
  friends appear ~30 times and are idiomatic.
- **Use `TestPublisher`/`TestSingle`/`TestCompletable`** when you need to
  *drive* signals rather than count them.
- **Write a shared fake** when several tests need the same double. See
  `TestLoadBalancedConnection` in `servicetalk-loadbalancer` — a small
  interface with a static factory that pre-stubs the common methods.
- `Mockito.spy` is rare — 3 uses in the whole repo. Prefer a mock or a fake,
  and reach for a spy only to observe calls on a real collaborator you cannot
  otherwise inject.

## 8. Parameterized tests

Always pass an explicit `name`. About a fifth of existing tests omit it, so
copy the majority rather than the file next to you:

```java
@ParameterizedTest(name = "{displayName} [{index}] {arguments}")
@EnumSource(TimerBehaviorParam.class)
void dataAndTimeout(TimerBehaviorParam params) { ... }

@ParameterizedTest(name = "{displayName} [{index}]: unhealthy={0} failOpen={1}")
@CsvSource({"true,true", "true,false", "false,true", "false,false"})
void singleInactiveHostWithoutConnections(boolean unhealthy, boolean failOpen) { ... }
```

Use `@MethodSource` with `Named.of(...)` when the arguments are objects whose
`toString()` would be unreadable.

**Prune invalid combinations with an assumption and a message.** This is
idiomatic here — 129 uses across 43 files:

```java
assumeFalse(api.isAggregated(), "This test asserts behavior only for streaming use-cases");
assumeTrue(protocol == HTTP_1);
```

Do **not** use an assumption to gate on a system property. A failed assumption
aborts the test and reports as skipped, so the coverage silently disappears
when the property flips.

## 9. What to test, and what to skip

Skip: getters and setters, `toString`, `equals`/`hashCode` without logic, and
null-input branches in packages annotated `@ElementsAreNonnullByDefault`
(which Checkstyle requires in every `package-info.java`, so it always holds).

**Do test every validation throw.** Grep the class under test for `throw new`
and make sure each site has a matching `assertThrows`. Gaps found this way in
the current repo include `RetryStrategiesTest` and `CacheSingleTest` — zero
`assertThrows` between them, though both classes validate their arguments — and
`OutlierDetectorConfig`, which has no test class at all.

Prefer deriving expected values from the production source of truth. Iterate
the real enum rather than hardcoding a list that will drift.

## 10. Adding a test to a module

Test dependencies are declared per module and an unused or undeclared one fails
`./gradlew quality`. The common lines, exactly as written in this repo:

```groovy
testImplementation enforcedPlatform("org.junit:junit-bom:$junit5Version")
testImplementation "org.junit.jupiter:junit-jupiter-api"
testImplementation "org.junit.jupiter:junit-jupiter-params"
testImplementation "org.hamcrest:hamcrest:$hamcrestVersion"
testImplementation "org.mockito:mockito-core:$mockitoCoreVersion"
```

Add whichever of these matches the harness you picked:

| Line | Gives you |
|---|---|
| `testImplementation project(":servicetalk-test-resources")` | `DefaultTestCerts`, `TestUtils.assertNoAsyncErrors`, the shared log4j2 config |
| `testImplementation testFixtures(project(":servicetalk-concurrent-internal"))` | `DeliberateException`, `TestTimeoutConstants`, `TimeoutTracingInfoExtension` |
| `testImplementation testFixtures(project(":servicetalk-concurrent-api"))` | `TestPublisher`, `TestSingle`, `TestCompletable`, `ExecutorExtension`, `BlockingTestUtils` |
| `testImplementation project(":servicetalk-concurrent-test-internal")` | `TestPublisherSubscriber`, `TestSingleSubscriber`, `TestCompletableSubscriber`, `AwaitUtils` |
| `testImplementation testFixtures(project(":servicetalk-transport-netty-internal"))` | `AddressUtils`, `ExecutionContextExtension`, `EmbeddedDuplexChannel`, `CloseUtils` |
| `testImplementation project(":servicetalk-buffer-netty")` | Buffer allocators for tests |

## 11. Before you claim done

```shell
./gradlew :servicetalk-<module>:test --tests "io.servicetalk.<pkg>.<Class>Test"
./gradlew :servicetalk-<module>:test
./gradlew :servicetalk-<module>:quality
```

- A hanging test is diagnosed from the `TimeoutTracingInfoExtension` thread
  dump printed just before the timeout fires. Read it before guessing.
- To see wire-level traffic:
  `./gradlew :servicetalk-http-netty:test -Dservicetalk.logger.wireLogLevel=DEBUG`.
  Other keys are `h2FrameLogLevel`, `lifecycleObserverLogLevel`, and `rootLevel`.
- To check whether the lines you changed are covered:
  `./gradlew :servicetalk-<module>:jacocoTestReport`, then open
  `servicetalk-<module>/build/reports/jacoco/test/html/index.html`. Coverage is
  a prompt to think, not a target — the repo sets no threshold.
