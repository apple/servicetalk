/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Future;

import static io.servicetalk.concurrent.api.Completable.mergeAll;
import static io.servicetalk.concurrent.api.Single.collectUnordered;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isIn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ClosableConcurrentStackTest {
    @RegisterExtension
    static final ExecutorExtension<Executor> EXECUTOR_RULE = ExecutorExtension.withCachedExecutor().setClassLevel(true);

    @Test
    void singleThreadPushClose() {
        ClosableConcurrentStack<Integer> stack = new ClosableConcurrentStack<>();
        final int itemCount = 1000;
        for (int i = 0; i < itemCount; ++i) {
            stack.push(i);
        }

        List<Integer> values = new ArrayList<>(itemCount);
        stack.close(values::add);

        assertEquals(itemCount, values.size());
        for (int i = itemCount - 1; i >= 0; --i) {
            assertEquals(values.get(itemCount - i - 1).intValue(), i);
        }
    }

    @Test
    void singleThreadPushRemove() {
        ClosableConcurrentStack<Integer> stack = new ClosableConcurrentStack<>();
        final int itemCount = 1000;
        for (int i = 0; i < itemCount; ++i) {
            stack.push(i);
        }

        for (int i = 0; i < itemCount; ++i) {
            assertTrue(stack.relaxedRemove(i));
        }
        closeAssertEmpty(stack);
    }

    @Test
    void concurrentPushClose() throws Exception {
        ClosableConcurrentStack<Integer> stack = new ClosableConcurrentStack<>();
        final int itemCount = 1000;
        CyclicBarrier barrier = new CyclicBarrier(itemCount + 1);
        List<Completable> completableList = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; ++i) {
            final int finalI = i;
            completableList.add(EXECUTOR_RULE.executor().submit(() -> {
                try {
                    barrier.await();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
                stack.push(finalI);
            }));
        }

        Queue<Integer> overallValues = new ConcurrentLinkedQueue<>();
        Future<Void> future = mergeAll(completableList, itemCount).toFuture();
        barrier.await();
        stack.close(overallValues::add);
        future.get();
        assertEquals(itemCount, overallValues.size());
        for (int i = 0; i < itemCount; ++i) {
            assertThat(i, isIn(overallValues));
        }
    }

    @Test
    void concurrentPushRemove() throws Exception {
        ClosableConcurrentStack<Integer> stack = new ClosableConcurrentStack<>();
        final int itemCount = 1000;
        CyclicBarrier barrier = new CyclicBarrier(itemCount + 1);
        List<Completable> completableList = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; ++i) {
            final int finalI = i;
            completableList.add(EXECUTOR_RULE.executor().submit(() -> {
                try {
                    barrier.await();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
                assertTrue(stack.push(finalI), () -> "failed for index: " + finalI);
                assertTrue(stack.relaxedRemove(finalI), () -> "failed for index: " + finalI);
            }));
        }

        Future<Void> future = mergeAll(completableList, itemCount).toFuture();
        barrier.await();
        future.get();
        closeAssertEmpty(stack);
    }

    @Test
    void concurrentPushRemoveDifferentThread() throws Exception {
        ClosableConcurrentStack<Integer> stack = new ClosableConcurrentStack<>();
        final int itemCount = 1000;
        CyclicBarrier barrier = new CyclicBarrier(itemCount + 1);
        List<Completable> completableList = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; ++i) {
            final int finalI = i;
            completableList.add(EXECUTOR_RULE.executor().submit(() -> {
                try {
                    barrier.await();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
                stack.push(finalI);
            }).concat(EXECUTOR_RULE.executor().submit(() ->
                    assertTrue(stack.relaxedRemove(finalI), () -> "failed for index: " + finalI))));
        }

        Future<Void> future = mergeAll(completableList, itemCount).toFuture();
        barrier.await();
        future.get();
        closeAssertEmpty(stack);
    }

    @Test
    void concurrentClosePushRemove() throws Exception {
        ClosableConcurrentStack<Cancellable> stack = new ClosableConcurrentStack<>();
        final int itemCount = 1000;
        CyclicBarrier barrier = new CyclicBarrier(itemCount + 1);
        List<Single<Cancellable>> completableList = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; ++i) {
            final int finalI = i;
            completableList.add(EXECUTOR_RULE.executor().submit(() -> {
                Cancellable c = mock(Cancellable.class);
                try {
                    barrier.await();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
                assertTrue(stack.push(c), () -> "failed for index: " + finalI);
                return c;
            }));
        }

        Future<Collection<Cancellable>> future = collectUnordered(completableList, itemCount).toFuture();
        barrier.await();
        Collection<Cancellable> cancellables = future.get();
        stack.close(Cancellable::cancel);
        assertFalse(stack.push(() -> { }));
        for (Cancellable c : cancellables) {
            verify(c).cancel();
        }
    }

    private static void closeAssertEmpty(ClosableConcurrentStack<Integer> stack) {
        List<Integer> values = new ArrayList<>();
        stack.close(values::add);
        assertThat(values, is(empty()));
        assertFalse(stack.push(-1));
        assertThat(values, contains(-1));
    }
}
