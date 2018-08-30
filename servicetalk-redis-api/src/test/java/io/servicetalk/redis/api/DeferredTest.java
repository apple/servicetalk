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
package io.servicetalk.redis.api;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

public class DeferredTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    private final Deferred<String> deferred = new Deferred<>();

    @Test
    public void onSuccess() {
        String value = "ok";
        deferred.onSuccess(value);
        assertThat(deferred.get(), is(value));
    }

    @Test
    public void onSuccessNull() {
        deferred.onSuccess(null);
        assertThat(deferred.get(), nullValue());
    }

    @Test
    public void onError() {
        Throwable t = new Throwable();
        deferred.onError(t);

        thrown.expect(is(t));
        deferred.get();
    }

    @Test
    public void onErrorNull() {
        thrown.expect(NullPointerException.class);
        deferred.onError(null);
    }

    @Test
    public void getWithoutValue() {
        thrown.expect(IllegalStateException.class);
        deferred.get();
    }

    @Test
    public void blockingGetBefore() throws Exception {
        Executor executor = Executors.newFixedSizeExecutor(1);
        try {
            Future<String> future = executor.submit((Callable<String>) deferred::blockingGet).toFuture();
            String value = "ok";
            deferred.onSuccess(value);
            assertThat(future.get(), is(value));
        } finally {
            executor.closeAsync().toFuture().get();
        }
    }

    @Test
    public void blockingGetAfter() {
        String value = "ok";
        deferred.onSuccess(value);
        assertThat(deferred.blockingGet(), is(value));
    }
}
