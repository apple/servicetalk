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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.concurrent.api.Single.collectUnordered;
import static io.servicetalk.concurrent.api.Single.collectUnorderedDelayError;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

public class CollectTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Test
    public void collectVarArgSuccess() throws Exception {
        Collection<Integer> integers = collectUnordered(succeeded(1), succeeded(2)).toFuture().get();
        assertThat("Unexpected result.", integers, containsInAnyOrder(1, 2));
    }

    @Test
    public void collectVarArgMaxConcurrencySuccess() throws Exception {
        // Just testing that the method works. As it uses existing operators, we don't require elaborate tests
        Collection<Integer> integers = collectUnordered(1, succeeded(1), succeeded(2)).toFuture().get();
        assertThat("Unexpected result.", integers, containsInAnyOrder(1, 2));
    }

    @Test
    public void collectVarArgFailure() throws Exception {
        Future<? extends Collection<Integer>> future =
                collectUnordered(failed(DELIBERATE_EXCEPTION), succeeded(2)).toFuture();
        try {
            future.get();
            fail();
        } catch (ExecutionException e) {
            assertThat("Unexpected exception.", e.getCause(), is(DELIBERATE_EXCEPTION));
        }
    }

    @Test
    public void collectVarArgDelayError() throws Exception {
        AtomicBoolean secondSubscribed = new AtomicBoolean();
        Future<? extends Collection<Integer>> future = collectUnorderedDelayError(failed(DELIBERATE_EXCEPTION),
                succeeded(2).beforeOnSubscribe(__ -> secondSubscribed.set(true))).toFuture();
        try {
            future.get();
            fail();
        } catch (ExecutionException e) {
            assertThat("Second source not subscribed.", secondSubscribed.get(), is(true));
            assertThat("Unexpected exception.", e.getCause(), is(DELIBERATE_EXCEPTION));
        }
    }

    @Test
    public void collectVarArgDelayErrorMaxConcurrency() throws Exception {
        AtomicBoolean secondSubscribed = new AtomicBoolean();
        Future<? extends Collection<Integer>> future = collectUnorderedDelayError(1, failed(DELIBERATE_EXCEPTION),
                succeeded(2).beforeOnSubscribe(__ -> secondSubscribed.set(true))).toFuture();
        try {
            future.get();
            fail();
        } catch (ExecutionException e) {
            assertThat("Second source not subscribed.", secondSubscribed.get(), is(true));
            assertThat("Unexpected exception.", e.getCause(), is(DELIBERATE_EXCEPTION));
        }
    }

    @Test
    public void collectIterableSuccess() throws Exception {
        Collection<Integer> integers = collectUnordered(asList(succeeded(1), succeeded(2))).toFuture().get();
        assertThat("Unexpected result.", integers, containsInAnyOrder(1, 2));
    }

    @Test
    public void collectIterableMaxConcurrencySuccess() throws Exception {
        // Just testing that the method works. As it uses existing operators, we don't require elaborate tests
        Collection<Integer> integers = collectUnordered(asList(succeeded(1), succeeded(2)), 1).toFuture().get();
        assertThat("Unexpected result.", integers, containsInAnyOrder(1, 2));
    }

    @Test
    public void collectIterableFailure() throws Exception {
        Future<? extends Collection<Integer>> future =
                collectUnordered(asList(failed(DELIBERATE_EXCEPTION), succeeded(2))).toFuture();
        try {
            future.get();
            fail();
        } catch (ExecutionException e) {
            assertThat("Unexpected exception.", e.getCause(), is(DELIBERATE_EXCEPTION));
        }
    }

    @Test
    public void collectIterableDelayError() throws Exception {
        AtomicBoolean secondSubscribed = new AtomicBoolean();
        Future<? extends Collection<Integer>> future = collectUnorderedDelayError(asList(failed(DELIBERATE_EXCEPTION),
                succeeded(2).beforeOnSubscribe(__ -> secondSubscribed.set(true)))).toFuture();
        try {
            future.get();
            fail();
        } catch (ExecutionException e) {
            assertThat("Second source not subscribed.", secondSubscribed.get(), is(true));
            assertThat("Unexpected exception.", e.getCause(), is(DELIBERATE_EXCEPTION));
        }
    }

    @Test
    public void collectIterableDelayErrorMaxConcurrency() throws Exception {
        AtomicBoolean secondSubscribed = new AtomicBoolean();
        Future<? extends Collection<Integer>> future = collectUnorderedDelayError(asList(failed(DELIBERATE_EXCEPTION),
                succeeded(2).beforeOnSubscribe(__ -> secondSubscribed.set(true))), 1).toFuture();
        try {
            future.get();
            fail();
        } catch (ExecutionException e) {
            assertThat("Second source not subscribed.", secondSubscribed.get(), is(true));
            assertThat("Unexpected exception.", e.getCause(), is(DELIBERATE_EXCEPTION));
        }
    }
}
