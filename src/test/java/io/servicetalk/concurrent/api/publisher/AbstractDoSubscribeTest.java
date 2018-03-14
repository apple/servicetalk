/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.Publisher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.reactivestreams.Subscription;

import java.util.function.Consumer;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public abstract class AbstractDoSubscribeTest {

    @Rule
    public final MockedSubscriberRule<String> rule = new MockedSubscriberRule<>();

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    private Consumer<Subscription> doOnSubscribe;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        doOnSubscribe = mock(Consumer.class);
    }

    @Test
    public void testOnSubscribe() {
        rule.subscribe(doSubscribe(Publisher.just("Hello"), doOnSubscribe)).verifySuccess("Hello");
        verify(doOnSubscribe).accept(any());
    }

    @Test
    public void testCallbackThrowsError() {
        thrown.expect(is(sameInstance(DELIBERATE_EXCEPTION)));

        Publisher<String> src = doSubscribe(Publisher.just("Hello"), s -> {
            throw DELIBERATE_EXCEPTION;
        });
        rule.subscribe(src).request(1);
    }

    protected abstract <T> Publisher<T> doSubscribe(Publisher<T> publisher, Consumer<Subscription> consumer);
}
