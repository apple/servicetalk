/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisherSubscriber;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.function.Consumer;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public abstract class AbstractDoSubscribeTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    final TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
    private Consumer<Subscription> doOnSubscribe;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        doOnSubscribe = mock(Consumer.class);
    }

    @Test
    public void testOnSubscribe() {
        toSource(doSubscribe(Publisher.just("Hello"), doOnSubscribe)).subscribe(subscriber);
        subscriber.request(1);
        assertThat(subscriber.items(), contains("Hello"));
        assertTrue(subscriber.isCompleted());
        verify(doOnSubscribe).accept(any());
    }

    protected abstract <T> Publisher<T> doSubscribe(Publisher<T> publisher, Consumer<Subscription> consumer);
}
