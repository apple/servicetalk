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
import io.servicetalk.concurrent.api.PublisherRule;
import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.Rule;
import org.junit.Test;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public abstract class AbstractDoCompleteTest {

    @Rule
    public final MockedSubscriberRule<String> rule = new MockedSubscriberRule<>();

    @Rule
    public final PublisherRule<String> publisher = new PublisherRule<>();

    @Test
    public void testComplete() {
        Runnable onComplete = mock(Runnable.class);
        rule.subscribe(doComplete(publisher.getPublisher(), onComplete));
        publisher.complete();
        rule.verifySuccess();
        verify(onComplete).run();
    }

    @Test
    public void testCallbackThrowsError() {
        DeliberateException srcEx = new DeliberateException();
        Publisher<String> src = doComplete(Publisher.error(srcEx), () -> {
            throw DELIBERATE_EXCEPTION;
        });
        rule.subscribe(src).requestAndVerifyFailure(srcEx);
    }

    protected abstract <T> Publisher<T> doComplete(Publisher<T> publisher, Runnable runnable);
}
