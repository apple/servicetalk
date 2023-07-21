/*
 * Copyright © 2018-2023 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkipWhileTest {

    private final TestPublisher<String> publisher = new TestPublisher<>();
    private final TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
    private final TestSubscription subscription = new TestSubscription();

    @ParameterizedTest(name = "doComplete={0} doCancel={1}")
    @CsvSource(value = {"false,false", "false,true", "true,false"})
    void skipWhile(boolean doComplete, boolean doCancel) {
        Publisher<String> p = publisher.skipWhile(s -> !"Hello2".equals(s));
        toSource(p).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(3);
        publisher.onNext("Hello1", "Hello2", "Hello3");
        assertThat(subscriber.takeOnNext(2), contains("Hello2", "Hello3"));
        if (doComplete) {
            publisher.onComplete();
            assertFalse(subscription.isCancelled());
            subscriber.awaitOnComplete();
        }
        if (doCancel) {
            subscriber.awaitSubscription().cancel();
            assertTrue(subscription.isCancelled());
        }
    }
}
