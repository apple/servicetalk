/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;

import static io.servicetalk.concurrent.api.Publisher.mergeAll;
import static io.servicetalk.concurrent.api.Publisher.mergeAllDelayError;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.sameInstance;

final class PublisherMergeWithTest {
    private TestPublisher<Integer> first;
    private TestPublisher<Integer> second;
    private TestPublisher<Integer> third;
    private TestPublisherSubscriber<Integer> subscriber;

    @BeforeEach
    void setUp() {
        first = new TestPublisher<>();
        second = new TestPublisher<>();
        third = new TestPublisher<>();
        subscriber = new TestPublisherSubscriber<>();
    }

    @SuppressWarnings("unused")
    private static Iterable<Arguments> completeSource() {
        List<Arguments> parameters = new ArrayList<>();
        for (boolean inOrderOnNext : asList(false, true)) {
            for (boolean inOrderTerminate : asList(false, true)) {
                for (boolean firstOnError : asList(false, true)) {
                    for (boolean delayError : asList(false, true)) {
                        parameters.add(Arguments.of(inOrderOnNext, inOrderTerminate, firstOnError, delayError));
                    }
                }
            }
        }
        return parameters;
    }

    @ParameterizedTest(name = "inOrderOnNext={0} inOrderTerminate={1} firstOnError={2} delayError={3}")
    @MethodSource("completeSource")
    void bothComplete(boolean inOrderOnNext, boolean inOrderTerminate, boolean firstOnError, boolean delayError) {
        toSource(delayError ? first.mergeDelayError(second) : first.merge(second)).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        int i = 3;
        int j = 4;
        if (inOrderOnNext) {
            first.onNext(i);
            second.onNext(j);
            assertThat(subscriber.takeOnNext(2), contains(i, j));
        } else {
            second.onNext(j);
            first.onNext(i);
            assertThat(subscriber.takeOnNext(2), contains(j, i));
        }

        if (inOrderTerminate) {
            if (firstOnError) {
                first.onError(DELIBERATE_EXCEPTION);

                if (!delayError) {
                    assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
                }
            } else {
                first.onComplete();
            }

            second.onComplete();
        } else {
            if (firstOnError) {
                second.onError(DELIBERATE_EXCEPTION);

                if (!delayError) {
                    assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
                }
            } else {
                second.onComplete();
            }
            first.onComplete();
        }

        if (!firstOnError) {
            subscriber.awaitOnComplete();
        } else if (delayError) {
            assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        }
    }

    @ParameterizedTest(name = "inOrderOnNext={0} inOrderTerminate={1} firstOnError={2} delayError={3}")
    @MethodSource("completeSource")
    void allComplete(boolean inOrderOnNext, boolean inOrderTerminate, boolean firstOnError, boolean delayError) {
        toSource(delayError ? mergeAllDelayError(first, second, third) : mergeAll(first, second, third))
                .subscribe(subscriber);
        subscriber.awaitSubscription().request(3);
        int i = 3;
        int j = 4;
        int x = 5;
        if (inOrderOnNext) {
            first.onNext(i);
            second.onNext(j);
            third.onNext(x);
            assertThat(subscriber.takeOnNext(3), contains(i, j, x));
        } else {
            second.onNext(j);
            third.onNext(x);
            first.onNext(i);
            assertThat(subscriber.takeOnNext(3), contains(j, x, i));
        }

        if (inOrderTerminate) {
            if (firstOnError) {
                first.onError(DELIBERATE_EXCEPTION);

                if (!delayError) {
                    assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
                }
            } else {
                first.onComplete();
            }

            second.onComplete();
            third.onComplete();
        } else {
            if (firstOnError) {
                third.onError(DELIBERATE_EXCEPTION);

                if (!delayError) {
                    assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
                }
            } else {
                third.onComplete();
            }
            second.onComplete();
            first.onComplete();
        }

        if (!firstOnError) {
            subscriber.awaitOnComplete();
        } else if (delayError) {
            assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        }
    }
}
