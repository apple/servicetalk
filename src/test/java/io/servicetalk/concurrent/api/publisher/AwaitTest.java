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
import io.servicetalk.concurrent.internal.EmptySubscription;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.reactivestreams.Subscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.internal.Await.await;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

public class AwaitTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Test
    public void testFailureNoEmission() throws Throwable {
        thrown.expectCause(is(DELIBERATE_EXCEPTION));
        awaitIndefinitely(Publisher.error(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testSuccess() throws Throwable {
        assertThat("Unexpected result.", awaitIndefinitely(just("Hello")), contains("Hello"));
    }

    @Test
    public void testFailureNoEmissionWithTimeout() throws Throwable {
        thrown.expectCause(is(DELIBERATE_EXCEPTION));
        await(Publisher.error(DELIBERATE_EXCEPTION), 1, MINUTES);
    }

    @Test
    public void testSuccessWithTimeout() throws Throwable {
        assertThat("Unexpected result.", await(just("Hello"), 1, MINUTES), contains("Hello"));
    }

    @Test
    public void testSuccessMultiWithTimeout() throws Throwable {
        assertThat("Unexpected result.", await(newSource(5, false), 1, MINUTES), hasSize(5));
    }

    @Test
    public void testFailurePostEmit() throws Throwable {
        thrown.expectCause(is(DELIBERATE_EXCEPTION));
        awaitIndefinitely(newSource(5, true));
    }

    @Test
    public void testFailurePostEmitWithTimeout() throws Throwable {
        thrown.expectCause(is(DELIBERATE_EXCEPTION));
        thrown.expectMessage(containsString(newItems(5).toString()));
        await(newSource(5, true), 1, MINUTES);
    }

    @Test
    public void testTimeout() throws Throwable {
        thrown.expect(instanceOf(TimeoutException.class));
        await(never(), 1, MILLISECONDS);
    }

    private static Publisher<String> newSource(int size, boolean terminateWithError) {
        return new Publisher<String>(immediate()) {
            @Override
            protected void handleSubscribe(Subscriber<? super String> s) {
                s.onSubscribe(EmptySubscription.EMPTY_SUBSCRIPTION);
                for (String item : newItems(size)) {
                    s.onNext(item);
                }
                if (terminateWithError) {
                    s.onError(DELIBERATE_EXCEPTION);
                } else {
                    s.onComplete();
                }
            }
        };
    }

    private static List<String> newItems(int size) {
        List<String> toReturn = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            toReturn.add("Hello" + i);
        }
        return toReturn;
    }
}
