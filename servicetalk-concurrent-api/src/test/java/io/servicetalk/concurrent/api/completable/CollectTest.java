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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.concurrent.api.Completable.collect;
import static io.servicetalk.concurrent.api.Completable.collectDelayError;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.fail;

public class CollectTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Test
    public void collectVarArgSuccess() throws Exception {
        collect(completed(), completed()).toFuture().get();
    }

    @Test
    public void collectVarArgMaxConcurrencySuccess() throws Exception {
        // Just testing that the method works. As it uses existing operators, we don't require elaborate tests
        collect(1, completed(), completed()).toFuture().get();
    }

    @Test
    public void collectVarArgFailure() throws Exception {
        AtomicBoolean secondSubscribed = new AtomicBoolean();
        Future<Void> future = collect(error(DELIBERATE_EXCEPTION),
                completed().doBeforeSubscribe(__ -> secondSubscribed.set(true))).toFuture();
        try {
            future.get();
            fail();
        } catch (ExecutionException e) {
            assertThat("Second source subscribed.", secondSubscribed.get(), is(false));
            assertThat("Unexpected exception.", e.getCause(), is(DELIBERATE_EXCEPTION));
        }
    }

    @Test
    public void collectVarArgDelayError() throws Exception {
        AtomicBoolean secondSubscribed = new AtomicBoolean();
        Future<Void> future = collectDelayError(error(DELIBERATE_EXCEPTION),
                completed().doBeforeSubscribe(__ -> secondSubscribed.set(true))).toFuture();
        try {
            future.get();
            fail();
        } catch (ExecutionException e) {
            assertThat("Second source not subscribed.", secondSubscribed.get(), is(true));
            assertThat("Unexpected exception.", e.getCause(), is(notNullValue()));
            assertThat("Unexpected exception cause.", e.getCause().getCause(), is(DELIBERATE_EXCEPTION));
        }
    }

    @Test
    public void collectVarArgDelayErrorMaxConcurrency() throws Exception {
        AtomicBoolean secondSubscribed = new AtomicBoolean();
        Future<Void> future = collectDelayError(1, error(DELIBERATE_EXCEPTION),
                completed().doBeforeSubscribe(__ -> secondSubscribed.set(true))).toFuture();
        try {
            future.get();
            fail();
        } catch (ExecutionException e) {
            assertThat("Second source not subscribed.", secondSubscribed.get(), is(true));
            assertThat("Unexpected exception.", e.getCause(), is(notNullValue()));
            assertThat("Unexpected exception cause.", e.getCause().getCause(), is(DELIBERATE_EXCEPTION));
        }
    }

    @Test
    public void collectIterableSuccess() throws Exception {
        collect(asList(completed(), completed())).toFuture().get();
    }

    @Test
    public void collectIterableMaxConcurrencySuccess() throws Exception {
        // Just testing that the method works. As it uses existing operators, we don't require elaborate tests
        collect(asList(completed(), completed()), 1).toFuture().get();
    }

    @Test
    public void collectIterableFailure() throws Exception {
        AtomicBoolean secondSubscribed = new AtomicBoolean();
        Future<Void> future = collect(asList(error(DELIBERATE_EXCEPTION),
                completed().doBeforeSubscribe(__ -> secondSubscribed.set(true)))).toFuture();
        try {
            future.get();
            fail();
        } catch (ExecutionException e) {
            assertThat("Second source subscribed.", secondSubscribed.get(), is(false));
            assertThat("Unexpected exception.", e.getCause(), is(DELIBERATE_EXCEPTION));
        }
    }

    @Test
    public void collectIterableDelayError() throws Exception {
        AtomicBoolean secondSubscribed = new AtomicBoolean();
        Future<Void> future = collectDelayError(asList(error(DELIBERATE_EXCEPTION),
                completed().doBeforeSubscribe(__ -> secondSubscribed.set(true)))).toFuture();
        try {
            future.get();
            fail();
        } catch (ExecutionException e) {
            assertThat("Second source not subscribed.", secondSubscribed.get(), is(true));
            assertThat("Unexpected exception.", e.getCause(), is(notNullValue()));
            assertThat("Unexpected exception cause.", e.getCause().getCause(), is(DELIBERATE_EXCEPTION));
        }
    }

    @Test
    public void collectIterableDelayErrorMaxConcurrency() throws Exception {
        AtomicBoolean secondSubscribed = new AtomicBoolean();
        Future<Void> future = collectDelayError(asList(error(DELIBERATE_EXCEPTION),
                completed().doBeforeSubscribe(__ -> secondSubscribed.set(true))), 1).toFuture();
        try {
            future.get();
            fail();
        } catch (ExecutionException e) {
            assertThat("Second source not subscribed.", secondSubscribed.get(), is(true));
            assertThat("Unexpected exception.", e.getCause(), is(notNullValue()));
            assertThat("Unexpected exception cause.", e.getCause().getCause(), is(DELIBERATE_EXCEPTION));
        }
    }
}
