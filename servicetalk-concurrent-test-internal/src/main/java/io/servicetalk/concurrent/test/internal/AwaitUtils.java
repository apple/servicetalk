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

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Utilities to do blocking await calls for tests.
 */
public final class AwaitUtils {
    private AwaitUtils() {
        // no instances
    }

    /**
     * Await a {@link CountDownLatch} while suppressing {@link InterruptedException} until the latch is counted down.
     * @param latch the {@link CountDownLatch} to await.
     */
    public static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        try {
            do {
                try {
                    latch.await();
                    return;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            } while (true);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Await a {@link CountDownLatch} while suppressing {@link InterruptedException} until the latch is counted down or
     * the given time duration expires.
     * @param latch the {@link CountDownLatch} to await.
     * @param timeout the timeout duration to await for.
     * @param unit the units applied to {@code timeout}.
     * @return see {@link CountDownLatch#await(long, TimeUnit)}.
     */
    public static boolean awaitUninterruptibly(CountDownLatch latch, long timeout, TimeUnit unit) {
        final long startTime = System.nanoTime();
        final long timeoutNanos = NANOSECONDS.convert(timeout, unit);
        long waitTime = timeoutNanos;
        boolean interrupted = false;
        try {
            do {
                try {
                    return latch.await(waitTime, NANOSECONDS);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
                waitTime = timeoutNanos - (System.nanoTime() - startTime);
                if (waitTime <= 0) {
                    return true;
                }
            } while (true);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * {@link BlockingQueue#take()} from the queue while suppressing {@link InterruptedException}s.
     * @param queue The queue to take from.
     * @param <T> The types of objects in the queue.
     * @return see {@link BlockingQueue#take()}.
     */
    public static <T> T takeUninterruptibly(BlockingQueue<T> queue) {
        boolean interrupted = false;
        try {
            do {
                try {
                    return queue.take();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            } while (true);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * {@link BlockingQueue#poll(long, TimeUnit)} from the queue while suppressing {@link InterruptedException}s.
     * @param queue the queue to poll from.
     * @param timeout the timeout duration to poll for.
     * @param unit the units applied to {@code timeout}.
     * @param <T> The types of objects in the queue.
     * @return see {@link BlockingQueue#poll(long, TimeUnit)}.
     */
    @Nullable
    public static <T> T pollUninterruptibly(BlockingQueue<T> queue, long timeout, TimeUnit unit) {
        final long startTime = System.nanoTime();
        final long timeoutNanos = NANOSECONDS.convert(timeout, unit);
        long waitTime = timeout;
        boolean interrupted = false;
        try {
            do {
                try {
                    return queue.poll(waitTime, NANOSECONDS);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
                waitTime = timeoutNanos - (System.nanoTime() - startTime);
                if (waitTime <= 0) {
                    return null;
                }
            } while (true);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
