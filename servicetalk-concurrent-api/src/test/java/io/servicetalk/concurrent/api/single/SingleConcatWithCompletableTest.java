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

import io.servicetalk.concurrent.api.TestCancellable;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.concurrent.api.TestSingle;
import io.servicetalk.concurrent.api.TestSingleSubscriber;

import org.junit.Before;
import org.junit.Test;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class SingleConcatWithCompletableTest {

    private TestSingleSubscriber<Integer> subscriber;
    private TestSingle<Integer> source;
    private TestCompletable next;

    @Before
    public void setUp() throws Exception {
        subscriber = new TestSingleSubscriber<>();
        source = new TestSingle<>();
        next = new TestCompletable();
        toSource(source.concat(next)).subscribe(subscriber);
    }

    @Test
    public void testSourceSuccessNextComplete() {
        source.onSuccess(1);
        assertThat(subscriber.result(), nullValue());
        assertThat(subscriber.error(), nullValue());
        next.onComplete();
        assertThat(subscriber.takeResult(), is(1));
    }

    @Test
    public void testSourceSuccessNextError() {
        source.onSuccess(1);
        assertThat(subscriber.result(), nullValue());
        assertThat(subscriber.error(), nullValue());
        next.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testSourceError() {
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
        assertFalse(next.isSubscribed());
    }

    @Test
    public void testCancelSource() {
        assertThat(subscriber.result(), nullValue());
        assertThat(subscriber.error(), nullValue());
        subscriber.cancel();
        TestCancellable cancellable = new TestCancellable();
        source.onSubscribe(cancellable);
        assertTrue(cancellable.isCancelled());
        assertFalse(next.isSubscribed());
    }

    @Test
    public void testCancelNext() {
        source.onSuccess(1);
        assertThat(subscriber.result(), nullValue());
        assertThat(subscriber.error(), nullValue());
        subscriber.cancel();
        TestCancellable sourceCancellable = new TestCancellable();
        source.onSubscribe(sourceCancellable);
        assertFalse(sourceCancellable.isCancelled());
        TestCancellable nextCancellable = new TestCancellable();
        next.onSubscribe(nextCancellable);
        assertTrue(nextCancellable.isCancelled());
    }
}
