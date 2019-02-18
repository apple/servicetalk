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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class DoBeforeFinallyTest extends AbstractDoFinallyTest {
    @Override
    protected <T> Publisher<T> doFinally(Publisher<T> publisher, Runnable runnable) {
        return publisher.doBeforeFinally(runnable);
    }

    @Override
    @Test
    public void testCallbackThrowsErrorOnComplete() {
        AtomicInteger invocationCount = new AtomicInteger();
        subscriber.subscribe(doFinally(publisher.publisher(), () -> {
            invocationCount.incrementAndGet();
            throw DELIBERATE_EXCEPTION;
        }));
        publisher.complete();
        subscriber.verifyFailure(DELIBERATE_EXCEPTION);
        assertThat("Unexpected calls to doFinally callback.", invocationCount.get(), is(1));
        publisher.verifyNotCancelled();
    }

    @Override
    @Test
    public void testCallbackThrowsErrorOnError() {
        DeliberateException exception = new DeliberateException();
        AtomicInteger invocationCount = new AtomicInteger();
        subscriber.subscribe(doFinally(publisher.publisher(), () -> {
            invocationCount.incrementAndGet();
            throw exception;
        }));
        publisher.fail();
        subscriber.verifyFailure(exception);
        assertThat("Unexpected calls to doFinally callback.", invocationCount.get(), is(1));
        publisher.verifyNotCancelled();
    }
}
