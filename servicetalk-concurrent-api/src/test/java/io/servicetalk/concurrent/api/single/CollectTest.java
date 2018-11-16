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

import org.junit.Test;

import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.api.Single.collect;
import static io.servicetalk.concurrent.api.Single.collectDelayError;
import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.success;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.fail;

public class CollectTest {

    @Test
    public void collectVarArgSuccess() throws Exception {
        Collection<Integer> integers = collect(success(1), success(2)).toFuture().get();
        assertThat("Unexpected result.", integers.stream().mapToInt(value -> value).sum(), is(3));
    }

    @Test
    public void collectVarArgMaxConcurrencySuccess() throws Exception {
        // Just testing that the method works. As it uses existing operators, we don't require elaborate tests
        Collection<Integer> integers = collect(1, success(1), success(2)).toFuture().get();
        assertThat("Unexpected result.", integers.stream().mapToInt(value -> value).sum(), is(3));
    }

    @Test
    public void collectVarArgFailure() throws Exception {
        AtomicBoolean secondSubscribed = new AtomicBoolean();
        Future<Collection<Integer>> future = collect(error(DELIBERATE_EXCEPTION),
                success(2).doBeforeSubscribe(__ -> secondSubscribed.set(true))).toFuture();
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
        Future<Collection<Integer>> future = collectDelayError(error(DELIBERATE_EXCEPTION),
                success(2).doBeforeSubscribe(__ -> secondSubscribed.set(true))).toFuture();
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
        Future<Collection<Integer>> future = collectDelayError(1, error(DELIBERATE_EXCEPTION),
                success(2).doBeforeSubscribe(__ -> secondSubscribed.set(true))).toFuture();
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
        Collection<Integer> integers = collect(asList(success(1), success(2))).toFuture().get();
        assertThat("Unexpected result.", integers.stream().mapToInt(value -> value).sum(), is(3));
    }

    @Test
    public void collectIterableMaxConcurrencySuccess() throws Exception {
        // Just testing that the method works. As it uses existing operators, we don't require elaborate tests
        Collection<Integer> integers = collect(asList(success(1), success(2)), 1).toFuture().get();
        assertThat("Unexpected result.", integers.stream().mapToInt(value -> value).sum(), is(3));
    }

    @Test
    public void collectIterableFailure() throws Exception {
        AtomicBoolean secondSubscribed = new AtomicBoolean();
        Future<Collection<Integer>> future = collect(asList(error(DELIBERATE_EXCEPTION),
                success(2).doBeforeSubscribe(__ -> secondSubscribed.set(true)))).toFuture();
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
        Future<Collection<Integer>> future = collectDelayError(asList(error(DELIBERATE_EXCEPTION),
                success(2).doBeforeSubscribe(__ -> secondSubscribed.set(true)))).toFuture();
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
        Future<Collection<Integer>> future = collectDelayError(asList(error(DELIBERATE_EXCEPTION),
                success(2).doBeforeSubscribe(__ -> secondSubscribed.set(true))), 1).toFuture();
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
