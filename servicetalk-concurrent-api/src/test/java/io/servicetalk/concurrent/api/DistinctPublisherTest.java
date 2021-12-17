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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

class DistinctPublisherTest {
    private final TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();

    @Test
    void passThrough() {
        toSource(Publisher.from(1, 2, 3).distinct()).subscribe(subscriber);
        subscriber.awaitSubscription().request(3);
        assertThat(subscriber.takeOnNext(3), contains(1, 2, 3));
        subscriber.awaitOnComplete();
    }

    @Test
    void duplicatesRemoved() {
        toSource(Publisher.from(1, 2, 2, 3).distinct()).subscribe(subscriber);
        subscriber.awaitSubscription().request(3);
        assertThat(subscriber.takeOnNext(3), contains(1, 2, 3));
        subscriber.awaitOnComplete();
    }

    @Test
    void duplicatesAllowedWhenKeyComparatorAllows() {
        toSource(Publisher.from(1, 2, 2, 3).filter(() -> new Predicate<Integer>() {
                    private final AtomicInteger count = new AtomicInteger();
                    private final Set<String> set = new HashSet<>();
                    @Override
                    public boolean test(final Integer i) {
                        count.incrementAndGet();
                        return set.add(i.toString() + "/" + count.incrementAndGet());
                    }
                })).subscribe(subscriber);
        subscriber.awaitSubscription().request(4);
        assertThat(subscriber.takeOnNext(4), contains(1, 2, 2, 3));
        subscriber.awaitOnComplete();
    }
}
