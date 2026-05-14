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
package io.servicetalk.client.api;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.Completable.completed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultClientGroupTest {

    @Test
    void requestingClientFromClosedClientGroupShouldNotHang() throws Exception {
        DefaultClientGroup<String, ListenableAsyncCloseable> cg =
                new DefaultClientGroup<>(s -> emptyAsyncCloseable());
        cg.closeAsync().toFuture().get();

        assertThrows(IllegalStateException.class, () -> cg.get("foo"));
        // Ensure this doesn't hang
        assertThrows(IllegalStateException.class, () -> cg.get("foo"));
    }

    @Test
    void clientCreatedConcurrentlyWithCloseIsClosed() throws Exception {
        CountDownLatch factoryEntered = new CountDownLatch(1);
        CountDownLatch releaseFactory = new CountDownLatch(1);
        AtomicBoolean clientClosed = new AtomicBoolean();
        ListenableAsyncCloseable client = new ListenableAsyncCloseable() {
            @Override
            public Completable onClose() {
                return completed();
            }

            @Override
            public Completable onClosing() {
                return completed();
            }

            @Override
            public Completable closeAsync() {
                clientClosed.set(true);
                return completed();
            }
        };

        DefaultClientGroup<String, ListenableAsyncCloseable> cg = new DefaultClientGroup<>(key -> {
            factoryEntered.countDown();
            try {
                releaseFactory.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            return client;
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ListenableAsyncCloseable> getFuture = executor.submit(() -> cg.get("foo"));

            // Wait until the creator thread is parked inside the factory
            assertThat(factoryEntered.await(5, TimeUnit.SECONDS), is(true));
            // Drain the group
            cg.closeAsync().toFuture().get();
            // Let the factory return. The creator should now close the client it just built and throw.
            releaseFactory.countDown();

            ExecutionException ee = assertThrows(ExecutionException.class,
                    () -> getFuture.get(5, TimeUnit.SECONDS));
            assertThat(ee.getCause(), instanceOf(IllegalStateException.class));
            assertThat("Freshly-created client must be closed when close wins the race",
                    clientClosed.get(), is(true));
        } finally {
            executor.shutdownNow();
        }
    }
}
