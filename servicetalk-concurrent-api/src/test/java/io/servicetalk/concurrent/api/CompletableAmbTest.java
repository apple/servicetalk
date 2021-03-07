/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.Completable.amb;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class CompletableAmbTest {

    private TestCompletable first;
    private TestCompletable second;
    private TestCompletableSubscriber subscriber;
    private TestCancellable cancellable;

    @BeforeEach
    void beforeEach() {
        first = new TestCompletable();
        second = new TestCompletable();
        subscriber = new TestCompletableSubscriber();
        cancellable = new TestCancellable();
    }

    private void setUp(final BiFunction<Completable, Completable, Completable> ambSupplier) {
        toSource(ambSupplier.apply(first, second)).subscribe(subscriber);
        subscriber.awaitSubscription();
        assertThat("First source not subscribed.", first.isSubscribed(), is(true));
        assertThat("Second source not subscribed.", second.isSubscribed(), is(true));
    }

    public static Collection<BiFunction<Completable, Completable, Completable>> data() {
        return asList(Completable::ambWith,
                (first, second) -> amb(first, second),
                (first, second) -> amb(asList(first, second)));
    }

    @ParameterizedTest(name = "{displayName} [{index}]")
    @MethodSource("data")
    public void successFirst(final BiFunction<Completable, Completable, Completable> ambSupplier) {
        setUp(ambSupplier);
        sendSuccessToAndVerify(first);
        verifyCancelled(second);
    }

    @ParameterizedTest(name = "{displayName} [{index}]")
    @MethodSource("data")
    public void successSecond(final BiFunction<Completable, Completable, Completable> ambSupplier) {
        setUp(ambSupplier);
        sendSuccessToAndVerify(second);
        verifyCancelled(first);
    }

    @ParameterizedTest(name = "{displayName} [{index}]")
    @MethodSource("data")
    public void failFirst(final BiFunction<Completable, Completable, Completable> ambSupplier) {
        setUp(ambSupplier);
        sendErrorToAndVerify(first);
        verifyCancelled(second);
    }

    @ParameterizedTest(name = "{displayName} [{index}]")
    @MethodSource("data")
    public void failSecond(final BiFunction<Completable, Completable, Completable> ambSupplier) {
        setUp(ambSupplier);
        sendErrorToAndVerify(second);
        verifyCancelled(first);
    }

    @ParameterizedTest(name = "{displayName} [{index}]")
    @MethodSource("data")
    public void successFirstThenSecond(final BiFunction<Completable, Completable, Completable> ambSupplier) {
        setUp(ambSupplier);
        sendSuccessToAndVerify(first);
        verifyCancelled(second);
        second.onComplete();
    }

    @ParameterizedTest(name = "{displayName} [{index}]")
    @MethodSource("data")
    public void successSecondThenFirst(final BiFunction<Completable, Completable, Completable> ambSupplier) {
        setUp(ambSupplier);
        sendSuccessToAndVerify(second);
        verifyCancelled(first);
        first.onComplete();
    }

    @ParameterizedTest(name = "{displayName} [{index}]")
    @MethodSource("data")
    public void failFirstThenSecond(final BiFunction<Completable, Completable, Completable> ambSupplier) {
        setUp(ambSupplier);
        sendErrorToAndVerify(first);
        verifyCancelled(second);
        second.onError(DELIBERATE_EXCEPTION);
    }

    @ParameterizedTest(name = "{displayName} [{index}]")
    @MethodSource("data")
    public void failSecondThenFirst(final BiFunction<Completable, Completable, Completable> ambSupplier) {
        setUp(ambSupplier);
        sendErrorToAndVerify(second);
        verifyCancelled(first);
        first.onError(DELIBERATE_EXCEPTION);
    }

    @ParameterizedTest(name = "{displayName} [{index}]")
    @MethodSource("data")
    public void successFirstThenSecondFail(final BiFunction<Completable, Completable, Completable> ambSupplier) {
        setUp(ambSupplier);
        sendSuccessToAndVerify(first);
        verifyCancelled(second);
        second.onError(DELIBERATE_EXCEPTION);
    }

    @ParameterizedTest(name = "{displayName} [{index}]")
    @MethodSource("data")
    public void successSecondThenFirstFail(final BiFunction<Completable, Completable, Completable> ambSupplier) {
        setUp(ambSupplier);
        sendSuccessToAndVerify(second);
        verifyCancelled(first);
        first.onError(DELIBERATE_EXCEPTION);
    }

    @ParameterizedTest(name = "{displayName} [{index}]")
    @MethodSource("data")
    public void failFirstThenSecondSuccess(final BiFunction<Completable, Completable, Completable> ambSupplier) {
        setUp(ambSupplier);
        sendErrorToAndVerify(first);
        verifyCancelled(second);
        second.onComplete();
    }

    @ParameterizedTest(name = "{displayName} [{index}]")
    @MethodSource("data")
    public void failSecondThenFirstSuccess(final BiFunction<Completable, Completable, Completable> ambSupplier) {
        setUp(ambSupplier);
        sendErrorToAndVerify(second);
        verifyCancelled(first);
        first.onComplete();
    }

    private void sendSuccessToAndVerify(final TestCompletable source) {
        source.onComplete();
        subscriber.awaitOnComplete();
    }

    private void sendErrorToAndVerify(final TestCompletable source) {
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    private void verifyCancelled(final TestCompletable other) {
        other.onSubscribe(cancellable);
        assertThat("Other source not cancelled.", cancellable.isCancelled(), is(true));
    }
}
