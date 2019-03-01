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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;

public class DoBeforeFinallyTest extends AbstractDoFinallyTest {
    @Override
    protected <T> PublisherSource<T> doFinally(Publisher<T> publisher, Runnable runnable) {
        return toSource(publisher.doBeforeFinally(runnable));
    }

    @Override
    @Test
    public void testCallbackThrowsErrorOnComplete() {
        AtomicInteger invocationCount = new AtomicInteger();
        doFinally(publisher, () -> {
            invocationCount.incrementAndGet();
            throw DELIBERATE_EXCEPTION;
        }).subscribe(subscriber);
        assertFalse(subscription.isCancelled());
        publisher.onComplete();
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
        assertThat("Unexpected calls to doFinally callback.", invocationCount.get(), is(1));
        assertFalse(subscription.isCancelled());
    }

    @Override
    @Test
    public void testCallbackThrowsErrorOnError() {
        DeliberateException exception = new DeliberateException();
        AtomicInteger invocationCount = new AtomicInteger();
        doFinally(publisher, () -> {
            invocationCount.incrementAndGet();
            throw exception;
        }).subscribe(subscriber);
        publisher.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeError(), sameInstance(exception));
        assertThat("Unexpected calls to doFinally callback.", invocationCount.get(), is(1));
        assertFalse(subscription.isCancelled());
    }
}
