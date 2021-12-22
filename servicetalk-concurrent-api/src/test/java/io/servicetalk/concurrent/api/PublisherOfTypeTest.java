/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

class PublisherOfTypeTest {
    private final TestPublisher<Object> source = new TestPublisher<>();
    private final TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();

    @Test
    void allSignals() {
        toSource(source.ofType(Integer.class)).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        source.onNext(1, 2);
        assertThat(subscriber.takeOnNext(2), contains(1, 2));
        source.onComplete();
        subscriber.awaitOnComplete();
    }

    @ParameterizedTest
    @MethodSource("someSignalsParams")
    void someSignals(String param) {
        toSource(source.ofType(Integer.class)).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        source.onNext(1, param);
        assertThat(subscriber.takeOnNext(), is(1));
        source.onComplete();
        subscriber.awaitOnComplete();
    }

    @Test
    void noSignals() {
        toSource(source.ofType(Integer.class)).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        source.onNext("NotInteger1", "NotInteger2");
        source.onComplete();
        subscriber.awaitOnComplete();
    }

    private static Stream<String> someSignalsParams() {
        return Stream.of("NotInteger", null);
    }
}
