/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.sameInstance;

public class ConcatPublisherTest {

    private final TestPublisher<String> first = new TestPublisher<>();
    private final TestPublisher<String> second = new TestPublisher<>();
    private final TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();

    @Test
    public void testEnoughRequests() {
        Publisher<String> p = first.concat(second);
        toSource(p).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        first.onNext("Hello1", "Hello2");
        first.onComplete();
        subscriber.awaitSubscription().request(2);
        second.onNext("Hello3", "Hello4");
        second.onComplete();
        assertThat(subscriber.takeOnNext(4), contains("Hello1", "Hello2", "Hello3", "Hello4"));
        subscriber.awaitOnComplete();
    }

    @Test
    public void testFirstEmitsError() {
        Publisher<String> p = first.concat(second);
        toSource(p).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        first.onNext("Hello1", "Hello2");
        first.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeOnNext(2), contains("Hello1", "Hello2"));
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testSecondEmitsError() {
        Publisher<String> p = first.concat(second);
        toSource(p).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        first.onNext("Hello1", "Hello2");
        first.onComplete();
        second.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeOnNext(2), contains("Hello1", "Hello2"));
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }
}
