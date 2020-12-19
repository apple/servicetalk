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

import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Future;

import static io.servicetalk.concurrent.api.Single.collectUnordered;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isIn;
import static org.junit.Assert.assertEquals;

public class ClosableConcurrentStackTest {
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
    public void concurrentPushClose() throws Exception {
        ClosableConcurrentStack<Integer> stack = new ClosableConcurrentStack<>();
        final int itemCount = 1000;
        CyclicBarrier barrier = new CyclicBarrier(itemCount + 1);
        List<Single<Integer>> completableList = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; ++i) {
            final int finalI = i;
            completableList.add(EXECUTOR_RULE.executor().submit(() -> {
                try {
                    barrier.await();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
                return stack.push(finalI) ? null : finalI;
            }));
        }

        List<Integer> overallValues = new ArrayList<>(itemCount);
        Future<Collection<Integer>> future = collectUnordered(completableList, itemCount).toFuture();
        barrier.await();
        stack.close(overallValues::add);
        Collection<Integer> failedPushValues = future.get();
        for (Integer integer : failedPushValues) {
            if (integer != null) {
                overallValues.add(integer);
            }
        }
        assertEquals(itemCount, overallValues.size());
        for (int i = 0; i < itemCount; ++i) {
            assertThat(i, isIn(overallValues));
        }
    }
}
