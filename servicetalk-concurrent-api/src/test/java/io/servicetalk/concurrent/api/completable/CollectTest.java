/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.concurrent.api.Completable.mergeAll;
import static io.servicetalk.concurrent.api.Completable.mergeAllDelayError;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

public class CollectTest {

    @Test
    public void collectVarArgSuccess() throws Exception {
        mergeAll(completed(), completed()).toFuture().get();
    }

    @Test
    public void collectVarArgMaxConcurrencySuccess() throws Exception {
        // Just testing that the method works. As it uses existing operators, we don't require elaborate tests
        mergeAll(1, completed(), completed()).toFuture().get();
    }

    @Test
    public void collectVarArgFailure() throws Exception {
        Future<Void> future = mergeAll(failed(DELIBERATE_EXCEPTION), completed()).toFuture();
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
        Future<Void> future = mergeAllDelayError(failed(DELIBERATE_EXCEPTION),
                completed().beforeOnSubscribe(__ -> secondSubscribed.set(true))).toFuture();
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
        Future<Void> future = mergeAllDelayError(1, failed(DELIBERATE_EXCEPTION),
                completed().beforeOnSubscribe(__ -> secondSubscribed.set(true))).toFuture();
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
        mergeAll(asList(completed(), completed())).toFuture().get();
    }

    @Test
    public void collectIterableMaxConcurrencySuccess() throws Exception {
        // Just testing that the method works. As it uses existing operators, we don't require elaborate tests
        mergeAll(asList(completed(), completed()), 1).toFuture().get();
    }

    @Test
    public void collectIterableFailure() throws Exception {
        Future<Void> future = mergeAll(asList(failed(DELIBERATE_EXCEPTION), completed())).toFuture();
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
        Future<Void> future = mergeAllDelayError(asList(failed(DELIBERATE_EXCEPTION),
                completed().beforeOnSubscribe(__ -> secondSubscribed.set(true)))).toFuture();
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
        Future<Void> future = mergeAllDelayError(asList(failed(DELIBERATE_EXCEPTION),
                completed().beforeOnSubscribe(__ -> secondSubscribed.set(true))), 1).toFuture();
        try {
            future.get();
            fail();
        } catch (ExecutionException e) {
            assertThat("Second source not subscribed.", secondSubscribed.get(), is(true));
            assertThat("Unexpected exception.", e.getCause(), is(DELIBERATE_EXCEPTION));
        }
    }
}
