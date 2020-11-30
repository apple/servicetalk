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
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.function.Consumer;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public abstract class AbstractWhenOnSubscribeTest {

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
        toSource(doSubscribe(from("Hello"), doOnSubscribe)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        assertThat(subscriber.takeOnNext(), is("Hello"));
        subscriber.awaitOnComplete();
        verify(doOnSubscribe).accept(any());
    }

    protected abstract <T> Publisher<T> doSubscribe(Publisher<T> publisher, Consumer<Subscription> consumer);
}
