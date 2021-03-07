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

import java.util.function.BiFunction;
import java.util.stream.Stream;

import static io.servicetalk.concurrent.api.Single.amb;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

public class SingleAmbSubscribeThrowsTest {

    private volatile boolean throwFromFirst;
    private volatile boolean throwFromSecond;
    private final TestSingle<Integer> first = new TestSingle<>();
    private final TestSingle<Integer> second = new TestSingle<>();
    private final TestCancellable cancellable = new TestCancellable();
    private final TestSingleSubscriber<Integer> subscriber = new TestSingleSubscriber<>();
    private Single<Integer> amb;

    private void init(final BiFunction<Single<Integer>, Single<Integer>, Single<Integer>> ambSupplier) {
        amb = ambSupplier.apply(defer(() -> {
            if (throwFromFirst) {
                throw DELIBERATE_EXCEPTION;
            }
            return first;
        }), defer(() -> {
            if (throwFromSecond) {
                throw DELIBERATE_EXCEPTION;
            }
            return second;
        }));
    }

    public static Stream<BiFunction<Single<Integer>, Single<Integer>, Single<Integer>>> data() {
        return Stream.of(Single::ambWith,
                (first, second) -> amb(first, second),
                (first, second) -> amb(asList(first, second)));
    }

    @ParameterizedTest
    @MethodSource("data")
    public void firstSubscribeThrows(final BiFunction<Single<Integer>, Single<Integer>, Single<Integer>> ambSupplier) {
        init(ambSupplier);
        throwFromFirst = true;
        subscribeToAmbAndVerifyFail();
        second.onSubscribe(cancellable);
        assertThat("Other source not cancelled.", cancellable.isCancelled(), is(true));

        second.onSuccess(2);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void secondSubscribeThrows(final BiFunction<Single<Integer>, Single<Integer>, Single<Integer>> ambSupplier) {
        init(ambSupplier);
        throwFromSecond = true;
        subscribeToAmbAndVerifyFail();
        first.onSubscribe(cancellable);
        assertThat("Other source not cancelled.", cancellable.isCancelled(), is(true));

        first.onSuccess(1);
    }

    private void subscribeToAmbAndVerifyFail() {
        toSource(amb).subscribe(subscriber);
        subscriber.awaitSubscription();
        assertThat("Unexpected error result.", subscriber.awaitOnError(), is(sameInstance(DELIBERATE_EXCEPTION)));
    }
}
