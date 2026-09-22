# Testing reactive sources

How to test a `Publisher`, `Single`, or `Completable` operator. Read
[../SKILL.md](../SKILL.md) first for the rules that apply to every test.

## Where the helpers live

| Class | Module | Gradle line |
|---|---|---|
| `TestPublisher`, `TestSingle`, `TestCompletable`, `TestSubscription`, `TestCancellable`, `TestExecutor`, `ExecutorExtension` | `servicetalk-concurrent-api/src/testFixtures` | `testImplementation testFixtures(project(":servicetalk-concurrent-api"))` |
| `TestPublisherSubscriber`, `TestSingleSubscriber`, `TestCompletableSubscriber`, `AwaitUtils` | `servicetalk-concurrent-test-internal/src/main` | `testImplementation project(":servicetalk-concurrent-test-internal")` |
| `DeliberateException`, `TestTimeoutConstants` | `servicetalk-concurrent-internal/src/testFixtures` | `testImplementation testFixtures(project(":servicetalk-concurrent-internal"))` |

## The shape

1. Build the operator under test around a `TestPublisher<T>` (or
   `Publisher.from(...)`, `Single.fromCallable(...)`).
2. Subscribe a `TestPublisherSubscriber<T>` through `toSource(...)`.
3. Drive signals by hand: `publisher.onNext(...)`, `onComplete()`, `onError(...)`.
4. Assert with the subscriber's blocking helpers.

`toSource(...).subscribe(...)` is the entry point, because `subscribe` on the
public `Publisher` type does not take a raw `Subscriber`.

```java
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
```

## `TestPublisherSubscriber` methods

| Method | Returns | Notes |
|---|---|---|
| `awaitSubscription()` | `Subscription` | Blocks until `onSubscribe`. Call `.request(n)` on the result. |
| `takeOnNext()` | `T` | Removes and returns the next item; fails if none. |
| `takeOnNext(int n)` | `List<T>` | Removes and returns exactly `n` items. |
| `pollAllOnNext()` | `List<T>` | Drains whatever has arrived so far. |
| `pollOnNext(long, TimeUnit)` | `Supplier<T>` | **Null when nothing arrived.** The `Supplier` wrapper lets a legitimate `null` item be distinguished from "no item". |
| `awaitOnComplete()` | `void` | Blocks for `onComplete`; also verifies every `onNext` was consumed. |
| `awaitOnError()` | `Throwable` | Blocks for `onError` and returns the error. |
| `pollTerminal(long, TimeUnit)` | `Supplier<Throwable>` | Null when no terminal signal yet. |

`TestSingleSubscriber` and `TestCompletableSubscriber` expose the same shape.
`TestCompletableSubscriber` delegates to a `TestPublisherSubscriber<Void>`.

## Deterministic time: never sleep

Use `TestExecutor` through `ExecutorExtension.withTestExecutor()` and advance
the clock yourself.

| Method | Purpose |
|---|---|
| `advanceTimeBy(time, unit)` | Moves the clock and runs everything now due. |
| `advanceTimeByNoExecuteTasks(time, unit)` | Moves the clock without running tasks. |
| `executeScheduledTasks()` / `executeNextScheduledTask()` | Run due scheduled tasks explicitly. |
| `executeTasks()` / `executeNextTask()` | Run queued (non-scheduled) tasks. |
| `scheduledTasksPending()` / `scheduledTasksExecuted()` | Assert on timer bookkeeping. |
| `currentTime(unit)` | Read the virtual clock. |

## Worked example: an operator with a timer

From `servicetalk-concurrent-api/src/test/java/io/servicetalk/concurrent/api/publisher/TimeoutPublisherTest.java`.
Note the `disableAutoOnSubscribe()` builder, used so the test controls when
`onSubscribe` fires and can observe the `TestSubscription`.

```java
@RegisterExtension
static final ExecutorExtension<TestExecutor> executorExtension = ExecutorExtension.withTestExecutor();

private final TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
private final TestSubscription subscription = new TestSubscription();
private final TestPublisher<Integer> publisher = new TestPublisher.Builder<Integer>().disableAutoOnSubscribe()
        .build(sub -> {
            sub.onSubscribe(subscription);
            return sub;
        });
private TestExecutor testExecutor;

@BeforeEach
void setup() {
    testExecutor = executorExtension.executor();
}

@AfterEach
void teardown() throws Exception {
    newCompositeCloseable().appendAll(testExecutor).close();
}

@ParameterizedTest(name = "{displayName} [{index}] {arguments}")
@EnumSource(TimerBehaviorParam.class)
void noDataOnCompletionNoTimeout(TimerBehaviorParam params) {
    init(params);

    subscriber.awaitSubscription().request(10);
    assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
    assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
    publisher.onComplete();
    subscriber.awaitOnComplete();

    assertThat(testExecutor.scheduledTasksPending(), is(0));
    assertThat(testExecutor.scheduledTasksExecuted(), is(0));
}
```

The same file drives a timeout deterministically with
`testExecutor.advanceTimeBy(millis, MILLISECONDS)` and then asserts
`assertThat(subscriber.awaitOnError(), instanceOf(TimeoutException.class))`.

## Worked example: counting signals with a mock

When you only need to know which callbacks fired and how often, mock the raw
`Subscriber` instead of building a `TestPublisherSubscriber`. From
`servicetalk-concurrent-api/src/test/java/io/servicetalk/concurrent/api/single/CallableSingleTest.java`:

```java
private static void listenAndVerify(Single<Integer> source) {
    @SuppressWarnings("unchecked")
    final SingleSource.Subscriber<Integer> subscriber = mock(SingleSource.Subscriber.class);
    toSource(source).subscribe(subscriber);
    verify(subscriber).onSubscribe(any());
    verify(subscriber).onSuccess(1);
    verifyNoMoreInteractions(subscriber);
}
```

Rule of thumb: **mock to count signals, use `TestPublisher` to drive them.**

## Expected failures

```java
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;

publisher.onError(DELIBERATE_EXCEPTION);
assertThat(subscriber.awaitOnError(), is(sameInstance(DELIBERATE_EXCEPTION)));
```

`DeliberateIOException` is the equivalent when the code path needs an
`IOException`.

## Interrupts

A test that cancels a subscription and asserts on interrupt delivery must
clear the flag afterwards, or it leaks onto the next test sharing the thread:

```java
} finally {
    Thread.interrupted();
}
```

See `ThreadInterruptingCancellableTest` and `CallableSingleTest.onSubscribeThrows`.

## AsyncContext propagation

Put a value in before subscribing, read it after termination:

```java
static final ContextMap.Key<String> KEY = newKey("share-context-key", String.class);

@Test
void contextIsShared() throws Exception {
    AsyncContext.put(KEY, "v1");
    awaitTermination(from(1).beforeOnNext(__ -> AsyncContext.put(KEY, "v2")).shareContextOnSubscribe());
    assertThat("Unexpected value found in the context.", AsyncContext.get(KEY), is("v2"));
}
```

Call `AsyncContext.clear()` in `@BeforeEach` when a test class manipulates
context keys directly. `AsyncContext.disable()`/`enable()` are process-global —
remember that test classes run concurrently.

## `StepVerifiers`

`servicetalk-concurrent-api-test` offers a Reactor-style fluent verifier
(`StepVerifiers.create(publisher).expectNext(...).expectComplete().verify()`).
It is used in only a handful of files outside its own module. Prefer
`TestPublisherSubscriber` unless you are editing one of those files.

## Reference examples

- `servicetalk-concurrent-api/src/test/java/io/servicetalk/concurrent/api/publisher/TimeoutPublisherTest.java`
  — the full pattern: `ExecutorExtension`, `TestPublisher.Builder`, deterministic time.
- `servicetalk-concurrent-api/src/test/java/io/servicetalk/concurrent/api/single/CallableSingleTest.java`
  — mock-based signal counting, and interrupt cleanup.
- `servicetalk-concurrent-internal/src/test/java/io/servicetalk/concurrent/internal/ThreadInterruptingCancellableTest.java`
  — a race-detection loop, and the one legitimate reason to add `@Timeout`.
