/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.test.internal;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Utilities to do blocking await calls for tests.
 */
public final class AwaitUtils {
    private AwaitUtils() {
        // no instances
    }

    /**
     * Await a {@link CountDownLatch} until the latch is counted down, throws unchecked InterruptedException.
     * @param latch the {@link CountDownLatch} to await.
     */
    public static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throwException(e);
        }
    }

    /**
     * Await a {@link CountDownLatch} until the latch is counted down or the given time duration expires,
     * throws unchecked InterruptedException.
     * @param latch the {@link CountDownLatch} to await.
     * @param timeout the timeout duration to await for.
     * @param unit the units applied to {@code timeout}.
     * @return true if the count reached zero and false if the waiting time elapsed before the count reached zero
     */
    public static boolean await(CountDownLatch latch, long timeout, TimeUnit unit) {
        final long timeoutNanos = NANOSECONDS.convert(timeout, unit);

        try {
            return latch.await(timeoutNanos, NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throwException(e);
            return false;
        }
    }

    /**
     * {@link BlockingQueue#take()} from the queue or throws unchecked exception in the case
     * of InterruptedException.
     * @param queue The queue to take from.
     * @param <T> The types of objects in the queue.
     * @return see {@link BlockingQueue#take()}.
     */
    @SuppressWarnings("unchecked")
    public static <T> T take(BlockingQueue<T> queue) {
        try {
            return queue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throwException(e);
            // Returning Object cast to type T is not necessary as the method will either return or throw exception
            // before even reaching this statement. However, this is necessary to compile successfully.
            return (T) new Object();
        }
    }

    /**
     * {@link BlockingQueue#poll(long, TimeUnit)} from the queue, throws unchecked Exception if
     * thread is interrupted while waiting.
     * @param queue the queue to poll from.
     * @param timeout the timeout duration to poll for.
     * @param unit the units applied to {@code timeout}.
     * @param <T> The types of objects in the queue.
     * @return see {@link BlockingQueue#poll(long, TimeUnit)}.
     */
    @Nullable
    public static <T> T poll(BlockingQueue<T> queue, long timeout, TimeUnit unit) {
        try {
            return queue.poll(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throwException(e);
            return null;
        }
    }
}
