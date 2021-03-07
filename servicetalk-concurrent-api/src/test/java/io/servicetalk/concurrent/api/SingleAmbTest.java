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

import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.Single.amb;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

public class SingleAmbTest {

    private final TestSingle<Integer> first = new TestSingle<>();
    private final TestSingle<Integer> second = new TestSingle<>();
    private final TestSingleSubscriber<Integer> subscriber = new TestSingleSubscriber<>();
    private final TestCancellable cancellable = new TestCancellable();

    private void init(final BiFunction<Single<Integer>, Single<Integer>,
            Single<Integer>> ambSupplier) {
        toSource(ambSupplier.apply(first, second)).subscribe(subscriber);
        subscriber.awaitSubscription();
        assertThat("First source not subscribed.", first.isSubscribed(), is(true));
        assertThat("Second source not subscribed.", second.isSubscribed(), is(true));
    }

    public static Collection<BiFunction<Single<Integer>, Single<Integer>, Single<Integer>>> data() {
        return asList(Single::ambWith,
                (first, second) -> amb(first, second),
                (first, second) -> amb(asList(first, second)));
    }

    @ParameterizedTest
    @MethodSource("data")
    public void successFirst(final BiFunction<Single<Integer>, Single<Integer>,
            Single<Integer>> ambSupplier) {
        init(ambSupplier);
        sendSuccessToAndVerify(first);
        verifyCancelled(second);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void successSecond(final BiFunction<Single<Integer>, Single<Integer>,
            Single<Integer>> ambSupplier) {
        init(ambSupplier);
        sendSuccessToAndVerify(second);
        verifyCancelled(first);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void failFirst(final BiFunction<Single<Integer>, Single<Integer>,
            Single<Integer>> ambSupplier) {
        init(ambSupplier);
        sendErrorToAndVerify(first);
        verifyCancelled(second);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void failSecond(final BiFunction<Single<Integer>, Single<Integer>,
            Single<Integer>> ambSupplier) {
        init(ambSupplier);
        sendErrorToAndVerify(second);
        verifyCancelled(first);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void successFirstThenSecond(final BiFunction<Single<Integer>, Single<Integer>,
            Single<Integer>> ambSupplier) {
        init(ambSupplier);
        sendSuccessToAndVerify(first);
        verifyCancelled(second);
        second.onSuccess(2);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void successSecondThenFirst(final BiFunction<Single<Integer>, Single<Integer>,
            Single<Integer>> ambSupplier) {
        init(ambSupplier);
        sendSuccessToAndVerify(second);
        verifyCancelled(first);
        first.onSuccess(2);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void failFirstThenSecond(final BiFunction<Single<Integer>, Single<Integer>,
            Single<Integer>> ambSupplier) {
        init(ambSupplier);
        sendErrorToAndVerify(first);
        verifyCancelled(second);
        second.onError(DELIBERATE_EXCEPTION);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void failSecondThenFirst(final BiFunction<Single<Integer>, Single<Integer>,
            Single<Integer>> ambSupplier) {
        init(ambSupplier);
        sendErrorToAndVerify(second);
        verifyCancelled(first);
        first.onError(DELIBERATE_EXCEPTION);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void successFirstThenSecondFail(final BiFunction<Single<Integer>, Single<Integer>,
            Single<Integer>> ambSupplier) {
        init(ambSupplier);
        sendSuccessToAndVerify(first);
        verifyCancelled(second);
        second.onError(DELIBERATE_EXCEPTION);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void successSecondThenFirstFail(final BiFunction<Single<Integer>, Single<Integer>,
            Single<Integer>> ambSupplier) {
        init(ambSupplier);
        sendSuccessToAndVerify(second);
        verifyCancelled(first);
        first.onError(DELIBERATE_EXCEPTION);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void failFirstThenSecondSuccess(final BiFunction<Single<Integer>, Single<Integer>,
            Single<Integer>> ambSupplier) {
        init(ambSupplier);
        sendErrorToAndVerify(first);
        verifyCancelled(second);
        second.onSuccess(2);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void failSecondThenFirstSuccess(final BiFunction<Single<Integer>, Single<Integer>,
            Single<Integer>> ambSupplier) {
        init(ambSupplier);
        sendErrorToAndVerify(second);
        verifyCancelled(first);
        first.onSuccess(2);
    }

    private void sendSuccessToAndVerify(final TestSingle<Integer> source) {
        source.onSuccess(1);
        assertThat("Unexpected result.", subscriber.awaitOnSuccess(), is(1));
    }

    private void sendErrorToAndVerify(final TestSingle<Integer> source) {
        source.onError(DELIBERATE_EXCEPTION);
        assertThat("Unexpected error result.", subscriber.awaitOnError(), is(sameInstance(DELIBERATE_EXCEPTION)));
    }

    private void verifyCancelled(final TestSingle<Integer> other) {
        other.onSubscribe(cancellable);
        assertThat("Other source not cancelled.", cancellable.isCancelled(), is(true));
    }
}
