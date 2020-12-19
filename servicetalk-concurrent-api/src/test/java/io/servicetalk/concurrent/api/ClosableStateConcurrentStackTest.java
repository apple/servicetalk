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

import static io.servicetalk.concurrent.api.Completable.mergeAll;
import static io.servicetalk.concurrent.api.Single.collectUnordered;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ClosableStateConcurrentStackTest {
    @ClassRule
    public static final ExecutorRule<Executor> EXECUTOR_RULE = ExecutorRule.newRule();

    @Test
    public void singleThreadPushClose() {
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
    public void singleThreadPushRemove() {
        ClosableStateConcurrentStack<Integer, String> stack = new ClosableStateConcurrentStack<>();
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
    public void concurrentPushRemove() throws Exception {
        ClosableStateConcurrentStack<Integer, String> stack = new ClosableStateConcurrentStack<>();
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
                assertNull(stack.push(finalI));
                assertTrue("failed for index: " + finalI, stack.relaxedRemove(finalI));
            }));
        }

        Future<Void> future = mergeAll(completableList, itemCount).toFuture();
        barrier.await();
        future.get();
        closeAssertEmpty(stack);
    }

    @Test
    public void concurrentPushRemoveDifferentThread() throws Exception {
        ClosableStateConcurrentStack<Integer, String> stack = new ClosableStateConcurrentStack<>();
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
                    assertTrue("failed for index: " + finalI, stack.relaxedRemove(finalI)))));
        }

        Future<Void> future = mergeAll(completableList, itemCount).toFuture();
        barrier.await();
        future.get();
        closeAssertEmpty(stack);
    }

    @Test
    public void concurrentClosePushRemove() throws Exception {
        ClosableStateConcurrentStack<Cancellable, String> stack = new ClosableStateConcurrentStack<>();
        final int itemCount = 1000;
        CyclicBarrier barrier = new CyclicBarrier(itemCount + 1);
        List<Single<Cancellable>> completableList = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; ++i) {
            completableList.add(EXECUTOR_RULE.executor().submit(() -> {
                Cancellable c = mock(Cancellable.class);
                try {
                    barrier.await();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
                assertNull(stack.push(c));
                return c;
            }));
        }

        Future<Collection<Cancellable>> future = collectUnordered(completableList, itemCount).toFuture();
        barrier.await();
        Collection<Cancellable> cancellables = future.get();
        stack.close(Cancellable::cancel, "closed");
        assertEquals("closed", stack.push(() -> { }));
        for (Cancellable c : cancellables) {
            verify(c).cancel();
        }
    }

    private static void closeAssertEmpty(ClosableStateConcurrentStack<Integer, String> stack) {
        List<Integer> values = new ArrayList<>();
        stack.close(values::add, "foo");
        assertThat(values, is(empty()));
        assertEquals("foo", stack.push(-1));
    }
}
