/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.api.AbstractToFutureTest;
import io.servicetalk.concurrent.api.TestSingle;

import org.junit.Test;

import java.util.concurrent.Future;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class SingleToFutureTest extends AbstractToFutureTest<Integer> {

    private final TestSingle<Integer> source = new TestSingle.Builder<Integer>().build(subscriber -> {
        subscriber.onSubscribe(mockCancellable);
        return subscriber;
    });

    @Override
    protected boolean isSubscribed() {
        return source.isSubscribed();
    }

    @Override
    protected Future<Integer> toFuture() {
        return source.toFuture();
    }

    @Override
    protected void completeSource() {
        source.onSuccess(1);
    }

    @Override
    protected void failSource(final Throwable t) {
        source.onError(t);
    }

    @Override
    protected Integer expectedResult() {
        return 1;
    }

    @Test
    public void testSucceededNull() throws Exception {
        Future<Integer> future = toFuture();
        exec.executor().schedule(() -> source.onSuccess(null), 10, MILLISECONDS);
        assertThat(future.get(), is(nullValue()));
        assertThat(future.isDone(), is(true));
        assertThat(future.isCancelled(), is(false));
        verify(mockCancellable, never()).cancel();
    }

    @Test
    public void testSucceededThrowable() throws Exception {
        TestSingle<Throwable> throwableSingle = new TestSingle<>();
        Future<Throwable> future = throwableSingle.toFuture();
        exec.executor().schedule(() -> throwableSingle.onSuccess(DELIBERATE_EXCEPTION), 10, MILLISECONDS);
        assertThat(future.get(), is(DELIBERATE_EXCEPTION));
        assertThat(future.isDone(), is(true));
        assertThat(future.isCancelled(), is(false));
        verify(mockCancellable, never()).cancel();
    }
}
