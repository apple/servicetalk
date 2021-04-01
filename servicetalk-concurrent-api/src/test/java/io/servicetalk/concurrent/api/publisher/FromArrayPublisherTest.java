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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;

import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class FromArrayPublisherTest extends FromInMemoryPublisherAbstractTest {
    @Override
    protected InMemorySource newPublisher(final Executor executor, final String[] values) {
        return new InMemorySource(values) {
            private final Publisher<String> publisher = from(values);
            @Override
            protected Publisher<String> publisher() {
                return publisher;
            }
        };
    }

    @Test
    public void testEmptyInvalidRequestAfterCompleteDoesNotDeliverOnError() {
        InMemorySource source = newSource(0);
        toSource(source.publisher()).subscribe(subscriber);
        subscriber.awaitOnComplete();
        assertThrows(IllegalArgumentException.class, () -> subscriber.awaitSubscription().request(-1));
    }
}
