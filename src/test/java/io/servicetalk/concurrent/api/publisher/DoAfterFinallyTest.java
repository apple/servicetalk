/**
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

import io.servicetalk.concurrent.api.DeliberateException;
import io.servicetalk.concurrent.api.Publisher;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.fail;

public class DoAfterFinallyTest extends AbstractDoFinallyTest {
    @Override
    protected <T> Publisher<T> doFinally(Publisher<T> publisher, Runnable runnable) {
        return publisher.doAfterFinally(runnable);
    }

    @Override
    @Test
    public void testCallbackThrowsErrorOnComplete() {
        thrown.expect(is(sameInstance(DELIBERATE_EXCEPTION)));
        AtomicInteger invocationCount = new AtomicInteger();
        try {
            subscriber.subscribe(doFinally(publisher.getPublisher(), () -> {
                invocationCount.incrementAndGet();
                throw DELIBERATE_EXCEPTION;
            }));
            publisher.complete();
            fail();
        } finally {
            subscriber.verifySuccess();
            assertThat("Unexpected calls to doFinally callback.", invocationCount.get(), is(1));
            publisher.verifyNotCancelled();
        }
    }

    @Override
    @Test
    public void testCallbackThrowsErrorOnError() {
        DeliberateException exception = new DeliberateException();
        thrown.expect(is(sameInstance(exception)));

        AtomicInteger invocationCount = new AtomicInteger();
        try {
            subscriber.subscribe(doFinally(publisher.getPublisher(), () -> {
                invocationCount.incrementAndGet();
                throw exception;
            }));
            publisher.fail();
            fail();
        } finally {
            subscriber.verifyFailure(DELIBERATE_EXCEPTION);
            assertThat("Unexpected calls to doFinally callback.", invocationCount.get(), is(1));
            publisher.verifyNotCancelled();
        }
    }
}
