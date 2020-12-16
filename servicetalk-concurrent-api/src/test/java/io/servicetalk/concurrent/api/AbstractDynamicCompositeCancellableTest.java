/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.Cancellable;

import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Future;

import static io.servicetalk.concurrent.api.Single.collectUnordered;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public abstract class AbstractDynamicCompositeCancellableTest {
    @ClassRule
    public static final ExecutorRule<Executor> EXECUTOR_RULE = ExecutorRule.newRule();

    protected abstract DynamicCompositeCancellable newCompositeCancellable();

    @Test
    public void testAddAndRemove() {
        DynamicCompositeCancellable c = newCompositeCancellable();
        Cancellable cancellable = mock(Cancellable.class);
        c.add(cancellable);
        c.remove(cancellable);
        c.cancel();
        verifyZeroInteractions(cancellable);
    }

    @Test
    public void testCancel() {
        DynamicCompositeCancellable c = newCompositeCancellable();
        Cancellable cancellable = mock(Cancellable.class);
        c.add(cancellable);
        c.cancel();
        verify(cancellable).cancel();
        c.remove(cancellable);
    }

    @Test
    public void testAddPostCancel() {
        DynamicCompositeCancellable c = newCompositeCancellable();
        c.cancel();
        Cancellable cancellable = mock(Cancellable.class);
        c.add(cancellable);
        verify(cancellable).cancel();
        c.remove(cancellable);
    }

    @Test
    public void duplicateAddDoesNotCancel() {
        DynamicCompositeCancellable c = newCompositeCancellable();
        Cancellable cancellable = mock(Cancellable.class);
        int addCount = 0;
        if (c.add(cancellable)) {
            ++addCount;
        }
        if (c.add(cancellable)) {
            ++addCount;
        }
        verify(cancellable, never()).cancel();

        for (int i = 0; i < addCount; ++i) {
            assertTrue(c.remove(cancellable));
        }
        c.cancel();
        verify(cancellable, never()).cancel();
    }

    @Test
    public void multiThreadedAddCancel() throws Exception {
        final int addThreads = 1000;
        final CyclicBarrier barrier = new CyclicBarrier(addThreads + 1);
        final List<Single<Cancellable>> cancellableSingles = new ArrayList<>(addThreads);
        DynamicCompositeCancellable dynamicCancellable = newCompositeCancellable();
        for (int i = 0; i < addThreads; ++i) {
            cancellableSingles.add(EXECUTOR_RULE.executor().submit(() -> {
                Cancellable c = mock(Cancellable.class);
                barrier.await();
                dynamicCancellable.add(c);
                return c;
            }));
        }

        Future<Collection<Cancellable>> future = collectUnordered(cancellableSingles, addThreads).toFuture();
        barrier.await();
        dynamicCancellable.cancel();
        Collection<Cancellable> cancellables = future.get();
        for (Cancellable c : cancellables) {
            verify(c).cancel();
        }
    }
}
