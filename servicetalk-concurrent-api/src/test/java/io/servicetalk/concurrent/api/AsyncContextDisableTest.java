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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.api.AsyncContextMap.Key;

import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AsyncContextDisableTest {
    private static final Key<String> K1 = Key.newKey("k1");

    @Test
    public void testDisableAsyncContext() throws ExecutionException, InterruptedException {
        Executor executor = Executors.newCachedThreadExecutor();
        Executor executor2 = null;
        try {
            // Test that AsyncContext is enabled first.
            String expectedValue = "foo";
            AsyncContext.put(K1, expectedValue);
            assertEquals(expectedValue, executor.submit(() -> AsyncContext.get(K1)).toFuture().get());
            AtomicReference<String> actualValue = new AtomicReference<>();
            int[] intArray = new int[] {1, 2};
            Publisher.from(intArray).doOnComplete(() -> actualValue.set(AsyncContext.get(K1)))
                    .toFuture().get();
            assertEquals(expectedValue, actualValue.get());
            actualValue.set(null);
            Single.success(1).doOnSuccess(i -> actualValue.set(AsyncContext.get(K1)))
                    .toFuture().get();
            assertEquals(expectedValue, actualValue.get());
            actualValue.set(null);
            Completable.completed().doOnComplete(() -> actualValue.set(AsyncContext.get(K1)))
                    .toFuture().get();
            actualValue.set(null);

            AsyncContext.disable();
            try {
                // Create a new Executor after we have disabled AsyncContext so we can be sure that AsyncContext won't
                // be captured.
                executor2 = Executors.newCachedThreadExecutor();
                AsyncContext.put(K1, expectedValue);
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
