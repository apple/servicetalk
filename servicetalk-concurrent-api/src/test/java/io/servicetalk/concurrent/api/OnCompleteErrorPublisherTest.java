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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;

final class OnCompleteErrorPublisherTest {
    private final TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();

    @Test
    void errorPassThrough() {
        toSource(Publisher.<Integer>failed(DELIBERATE_EXCEPTION)
                .onCompleteError(() -> new IllegalStateException("shouldn't get here"))
        ).subscribe(subscriber);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void nullCompletes() {
        toSource(Publisher.<Integer>empty()
                .onCompleteError(() -> null)
        ).subscribe(subscriber);
        subscriber.awaitOnComplete();
    }

    @ParameterizedTest(name = "{displayName} [{index}] shouldThrow={0}")
    @ValueSource(booleans = {false, true})
    void completeToError(boolean shouldThrow) {
        toSource(from(1)
                .onCompleteError(() -> {
                    if (shouldThrow) {
                        throw DELIBERATE_EXCEPTION;
                    }
                    return DELIBERATE_EXCEPTION;
                })
        ).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        assertThat(subscriber.takeOnNext(), equalTo(1));
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }
}
