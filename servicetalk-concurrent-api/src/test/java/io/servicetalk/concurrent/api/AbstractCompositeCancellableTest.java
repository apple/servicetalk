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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public abstract class AbstractCompositeCancellableTest<T extends Cancellable> {
    @ClassRule
    public static final ExecutorRule<Executor> EXECUTOR_RULE = ExecutorRule.newRule();

    protected abstract T newCompositeCancellable();

    protected abstract boolean add(T composite, Cancellable c);

    @Test
    public void testCancel() {
        T c = newCompositeCancellable();
        Cancellable cancellable = mock(Cancellable.class);
        add(c, cancellable);
        c.cancel();
        verify(cancellable).cancel();
    }

    @Test
    public void testAddPostCancel() {
        T c = newCompositeCancellable();
        c.cancel();
        Cancellable cancellable = mock(Cancellable.class);
        add(c, cancellable);
        verify(cancellable).cancel();
    }

    @Test
    public void multiThreadedAddCancel() throws Exception {
        final int addThreads = 1000;
        final CyclicBarrier barrier = new CyclicBarrier(addThreads + 1);
        final List<Single<Cancellable>> cancellableSingles = new ArrayList<>(addThreads);
        T dynamicCancellable = newCompositeCancellable();
        for (int i = 0; i < addThreads; ++i) {
            cancellableSingles.add(EXECUTOR_RULE.executor().submit(() -> {
                Cancellable c = mock(Cancellable.class);
                barrier.await();
                add(dynamicCancellable, c);
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
