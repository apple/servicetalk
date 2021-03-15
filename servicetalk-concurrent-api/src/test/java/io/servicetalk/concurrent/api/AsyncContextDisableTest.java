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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.api.AsyncContextMap.Key;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

public class AsyncContextDisableTest {
    private static final Key<String> K1 = Key.newKey("k1");

    @Test
    public void testDisableAsyncContext() throws Exception {
        synchronized (K1) { // prevent parallel execution because these tests rely upon static state
            Executor executor = Executors.newCachedThreadExecutor();
            Executor executor2 = null;
            try {
                // Test that AsyncContext is enabled first.
                String expectedValue = "foo";
                AsyncContext.put(K1, expectedValue);
                assertEquals(expectedValue, executor.submit(() -> AsyncContext.get(K1)).toFuture().get());
                AtomicReference<String> actualValue = new AtomicReference<>();
                Publisher.from(1, 2).publishOn(executor).beforeOnComplete(() -> actualValue.set(AsyncContext.get(K1)))
                        .toFuture().get();
                assertEquals(expectedValue, actualValue.get());
                actualValue.set(null);
                Single.succeeded(1).publishOn(executor).beforeOnSuccess(i -> actualValue.set(AsyncContext.get(K1)))
                        .toFuture().get();
                assertEquals(expectedValue, actualValue.get());
                actualValue.set(null);
                Completable.completed().publishOn(executor)
                        .beforeOnComplete(() -> actualValue.set(AsyncContext.get(K1))).toFuture().get();
                assertEquals(expectedValue, actualValue.get());
                actualValue.set(null);

                AsyncContext.disable();
                try {
                    // Create a new Executor after we have disabled AsyncContext so we can be sure that AsyncContext
                    // won't be captured.
                    executor2 = Executors.newCachedThreadExecutor();
                    asyncContextPutIgnoreUnsupported(K1, expectedValue);
                    assertNull(executor2.submit(() -> AsyncContext.get(K1)).toFuture().get());
                } finally {
                    AsyncContext.enable();
                }
            } finally {
                if (executor2 != null) {
                    executor2.closeAsync().toFuture().get();
                }
                executor.closeAsync().toFuture().get();
            }
        }
    }

    @Test
    public void testAutoEnableDoesNotOverrideDisable() throws Exception {
        synchronized (K1) { // prevent parallel execution because these tests rely upon static state
            AsyncContext.disable();
            try {
                Executor executor = Executors.newCachedThreadExecutor();
                try {
                    asyncContextPutIgnoreUnsupported(K1, "foo");
                    assertNull(executor.submit(() -> AsyncContext.get(K1)).toFuture().get());

                    AtomicReference<String> actualValue = new AtomicReference<>();
                    Publisher.from(1, 2).publishOn(executor)
                            .beforeOnComplete(() -> actualValue.set(AsyncContext.get(K1))).toFuture().get();
                    assertNull(actualValue.get());
                    actualValue.set(null);
                    Single.succeeded(1).publishOn(executor)
                            .beforeOnSuccess(i -> actualValue.set(AsyncContext.get(K1)))
                            .toFuture().get();
                    assertNull(actualValue.get());
                    actualValue.set(null);
                    Completable.completed().publishOn(executor)
                            .beforeOnComplete(() -> actualValue.set(AsyncContext.get(K1))).toFuture().get();
                    assertNull(actualValue.get());
                    actualValue.set(null);
                } finally {
                    executor.closeAsync().toFuture().get();
                }
            } finally {
                AsyncContext.enable();
            }
        }
    }

    private static <T> void asyncContextPutIgnoreUnsupported(Key<T> key, T value) {
        try {
            AsyncContext.put(key, value);
            fail(UnsupportedOperationException.class + " exception expected but not seen");
        } catch (UnsupportedOperationException ignored) {
            // expected
        }
    }
}
