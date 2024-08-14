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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.Completable.amb;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class CompletableAmbTest {

    private final TestCompletable first = new TestCompletable();
    private final TestCompletable second = new TestCompletable();
    private final TestCompletableSubscriber subscriber = new TestCompletableSubscriber();

    private enum AmbParam {
        AMB_WITH {
            @Override
            BiFunction<Completable, Completable, Completable> get() {
                return Completable::ambWith;
            }
        },
        AMB_VARARGS {
            @Override
            BiFunction<Completable, Completable, Completable> get() {
                return (first, second) -> amb(first, second);
            }
        },
        AMB_ITERABLE {
            @Override
            BiFunction<Completable, Completable, Completable> get() {
                return (first, second) -> amb(asList(first, second));
            }
        };

        abstract BiFunction<Completable, Completable, Completable> get();
    }

    private void setUp(final BiFunction<Completable, Completable, Completable> ambSupplier) {
        toSource(ambSupplier.apply(first, second)).subscribe(subscriber);
        subscriber.awaitSubscription();
        assertThat("First source not subscribed.", first.isSubscribed(), is(true));
        assertThat("Second source not subscribed.", second.isSubscribed(), is(true));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(AmbParam.class)
    void successFirst(final AmbParam ambParam) {
        setUp(ambParam.get());
        sendSuccessToAndVerify(first);
        verifyCancelled(second);
        verifyNotCancelled(first);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(AmbParam.class)
    void successSecond(final AmbParam ambParam) {
        setUp(ambParam.get());
        sendSuccessToAndVerify(second);
        verifyCancelled(first);
        verifyNotCancelled(second);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(AmbParam.class)
    void failFirst(final AmbParam ambParam) {
        setUp(ambParam.get());
        sendErrorToAndVerify(first);
        verifyCancelled(second);
        verifyNotCancelled(first);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(AmbParam.class)
    void failSecond(final AmbParam ambParam) {
        setUp(ambParam.get());
        sendErrorToAndVerify(second);
        verifyCancelled(first);
        verifyNotCancelled(second);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(AmbParam.class)
    void successFirstThenSecond(final AmbParam ambParam) {
        setUp(ambParam.get());
        sendSuccessToAndVerify(first);
        verifyCancelled(second);
        verifyNotCancelled(first);
        second.onComplete();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(AmbParam.class)
    void successSecondThenFirst(final AmbParam ambParam) {
        setUp(ambParam.get());
        sendSuccessToAndVerify(second);
        verifyCancelled(first);
        verifyNotCancelled(second);
        first.onComplete();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(AmbParam.class)
    void failFirstThenSecond(final AmbParam ambParam) {
        setUp(ambParam.get());
        sendErrorToAndVerify(first);
        verifyCancelled(second);
        verifyNotCancelled(first);
        second.onError(DELIBERATE_EXCEPTION);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(AmbParam.class)
    void failSecondThenFirst(final AmbParam ambParam) {
        setUp(ambParam.get());
        sendErrorToAndVerify(second);
        verifyCancelled(first);
        verifyNotCancelled(second);
        first.onError(DELIBERATE_EXCEPTION);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(AmbParam.class)
    void successFirstThenSecondFail(final AmbParam ambParam) {
        setUp(ambParam.get());
        sendSuccessToAndVerify(first);
        verifyCancelled(second);
        verifyNotCancelled(first);
        second.onError(DELIBERATE_EXCEPTION);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(AmbParam.class)
    void successSecondThenFirstFail(final AmbParam ambParam) {
        setUp(ambParam.get());
        sendSuccessToAndVerify(second);
        verifyCancelled(first);
        verifyNotCancelled(second);
        first.onError(DELIBERATE_EXCEPTION);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(AmbParam.class)
    void failFirstThenSecondSuccess(final AmbParam ambParam) {
        setUp(ambParam.get());
        sendErrorToAndVerify(first);
        verifyCancelled(second);
        verifyNotCancelled(first);
        second.onComplete();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(AmbParam.class)
    void failSecondThenFirstSuccess(final AmbParam ambParam) {
        setUp(ambParam.get());
        sendErrorToAndVerify(second);
        verifyCancelled(first);
        verifyNotCancelled(second);
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

    private static void verifyNotCancelled(final TestCompletable completable) {
        final TestCancellable cancellable = new TestCancellable();
        completable.onSubscribe(cancellable);
        assertThat("Completable cancelled when no cancellation was expected.", cancellable.isCancelled(), is(false));
    }

    private static void verifyCancelled(final TestCompletable completable) {
        final TestCancellable cancellable = new TestCancellable();
        completable.onSubscribe(cancellable);
        assertThat("Completable not cancelled, but cancellation was expected.", cancellable.isCancelled(), is(true));
    }
}
