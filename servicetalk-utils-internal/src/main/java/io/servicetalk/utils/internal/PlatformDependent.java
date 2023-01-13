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
/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.servicetalk.utils.internal;

import org.jctools.queues.MpscChunkedArrayQueue;
import org.jctools.queues.MpscLinkedQueue;
import org.jctools.queues.MpscUnboundedArrayQueue;
import org.jctools.queues.SpscChunkedArrayQueue;
import org.jctools.queues.SpscUnboundedArrayQueue;
import org.jctools.queues.atomic.MpscGrowableAtomicArrayQueue;
import org.jctools.queues.atomic.MpscLinkedAtomicQueue;
import org.jctools.queues.atomic.MpscUnboundedAtomicArrayQueue;
import org.jctools.queues.atomic.SpscGrowableAtomicArrayQueue;
import org.jctools.queues.atomic.SpscUnboundedAtomicArrayQueue;
import org.jctools.queues.unpadded.MpscChunkedUnpaddedArrayQueue;
import org.jctools.queues.unpadded.MpscLinkedUnpaddedQueue;
import org.jctools.queues.unpadded.MpscUnboundedUnpaddedArrayQueue;
import org.jctools.queues.unpadded.SpscChunkedUnpaddedArrayQueue;
import org.jctools.queues.unpadded.SpscUnboundedUnpaddedArrayQueue;
import org.jctools.util.Pow2;
import org.jctools.util.UnsafeAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.System.getProperty;

/**
 * Provide utilities that are dependent on the current runtime environment.
 * <p>
 * This class is forked from the netty project and modified to suit our needs.
 */
public final class PlatformDependent {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformDependent.class);

    private static final int QUEUE_CHUNK_SIZE = 1024;
    private static final int MIN_MAX_MPSC_CAPACITY = 4; // JCTools does not allow lower max capacity.
    private static final int MIN_MAX_SPSC_CAPACITY = 16; // JCTools does not allow lower max capacity.
    private static final int MAX_ALLOWED_QUEUE_CAPACITY = Pow2.MAX_POW2;
    private static final int MIN_ALLOWED_SPSC_CHUNK_SIZE = 8; // JCTools does not allow lower initial capacity.
    private static final int MIN_ALLOWED_MPSC_CHUNK_SIZE = 2; // JCTools does not allow lower initial capacity.

    private PlatformDependent() {
        // no instantiation
    }

    /**
     * Checks if {@code sun.misc.Unsafe} is available and has not been disabled.
     *
     * @return {@code true} if {@code sun.misc.Unsafe} is available.
     * @deprecated This method is not used by internal ServiceTalk code anymore and will be removed in future releases.
     */
    @Deprecated
    public static boolean hasUnsafe() { // FIXME: 0.43 - remove deprecated method
        return PlatformDependent0.hasUnsafe();
    }

    /**
     * Checks if it is possible to create a new direct {@link ByteBuffer} without zeroing the direct memory.
     *
     * @return {@code true} if it is possible to create a new direct {@link ByteBuffer} without zeroing the direct
     * memory; {@code false} otherwise.
     */
    public static boolean useDirectBufferWithoutZeroing() {
        return PlatformDependent0.useDirectBufferWithoutZeroing();
    }

    /**
     * Reserves direct memory for the specified size and capacity.
     *
     * @param size The size of direct memory to reserve.
     * @param capacity The capacity of direct memory to reserve.
     */
    public static void reserveMemory(final long size, final int capacity) {
        PlatformDependent0.reserveMemory(size, capacity);
    }

    /**
     * Unreserves direct memory for the specified size and capacity.
     *
     * @param size The size of direct memory to unreserve.
     * @param capacity The capacity of direct memory to unreserve.
     */
    public static void unreserveMemory(final long size, final int capacity) {
        PlatformDependent0.unreserveMemory(size, capacity);
    }

    /**
     * Allocates direct memory.
     *
     * @param size The size of direct memory to allocate.
     * @return The address of allocated memory.
     */
    public static long allocateMemory(final long size) {
        return PlatformDependent0.allocateMemory(size);
    }

    /**
     * Deallocates direct memory.
     *
     * @param address The address of direct memory to free.
     */
    public static void freeMemory(final long address) {
        PlatformDependent0.freeMemory(address);
    }

    /**
     * Creates a new {@link ByteBuffer} for the specified pre-allocated direct memory.
     *
     * @param address The address of pre-allocated direct memory.
     * @param size The size of pre-allocated direct memory.
     * @param capacity The capacity of pre-allocated direct memory.
     * @return A new {@link ByteBuffer}.
     */
    public static ByteBuffer newDirectBuffer(final long address, final long size, final int capacity) {
        return PlatformDependent0.newDirectBuffer(address, size, capacity);
    }

    /**
    * Raises an exception bypassing compiler checks for checked exceptions.
    *
    * @param t The {@link Throwable} to throw.
    * @param <T> The expected type
    * @return nothing actually will be returned from this method because it rethrows the specified exception. Making
    * this method return an arbitrary type makes the caller method easier as they do not have to add a return statement
    * after calling this method.
    * @deprecated Use {@link ThrowableUtils#throwException(Throwable)}.
    */
    @Deprecated
    public static <T> T throwException(final Throwable t) { // FIXME: 0.43 - remove deprecated method
        return ThrowableUtils.throwException(t);
    }

    /**
     * Create a new {@link Queue} which is safe to use for multiple producers (different threads) and a single
     * consumer (one thread!).
     *
     * @param <T> Type of items stored in the queue.
     * @return A new unbounded MPSC {@link Queue}.
     */
    public static <T> Queue<T> newUnboundedMpscQueue() {
        return newUnboundedMpscQueue(QUEUE_CHUNK_SIZE);
    }

    /**
     * Create a new {@link Queue} which is safe to use for multiple producers (different threads) and a single
     * consumer (one thread!).
     *
     * @param initialCapacity of the returned queue.
     * @param <T> Type of items stored in the queue.
     * @return A new unbounded MPSC {@link Queue}.
     */
    public static <T> Queue<T> newUnboundedMpscQueue(final int initialCapacity) {
        return Queues.newUnboundedMpscQueue(initialCapacity);
    }

    /**
     * Create a new {@link Queue} which is safe to use for multiple producers (different threads) and a single
     * consumer (one thread!).
     *
     * @param maxCapacity of the queue.
     * @param <T> Type of items stored in the queue.
     * @return A new MPSC {@link Queue} with max capacity of {@code maxCapacity}.
     */
    public static <T> Queue<T> newMpscQueue(final int maxCapacity) {
        return newMpscQueue(QUEUE_CHUNK_SIZE, maxCapacity);
    }

    /**
     * Create a new {@link Queue} which is safe to use for multiple producers (different threads) and a single
     * consumer (one thread!).
     *
     * @param initialCapacity Initial capacity for the queue.
     * @param maxCapacity of the queue.
     * @param <T> Type of items stored in the queue.
     * @return A new MPSC {@link Queue} with max capacity of {@code maxCapacity}.
     */
    public static <T> Queue<T> newMpscQueue(final int initialCapacity, final int maxCapacity) {
        return Queues.newMpscQueue(initialCapacity, maxCapacity);
    }

    /**
     * Create a new MPSC {@link Queue} that will use a linked data structure and supports {@link Queue#remove(Object)}.
     * <p>
     * Some reasons to use this queue as opposed to {@link #newUnboundedMpscQueue()} include the following:
     * <ul>
     *     <li>Lower Initial Memory Overhead - Currently the linked queues consume 392 bytes vs 568 for the array based
     *     queues. Also consider the JDK based {@link ConcurrentLinkedQueue} only has 24 bytes of initial overhead so
     *     this maybe a viable alternative if memory pressure exists and the size is expected to be small.</li>
     *     <li>{@link Queue#remove(Object)} support - Current only the linked variants support this operation.</li>
     * </ul>
     *
     * @param <T> The data type of the queue.
     * @return a new MPSC {@link Queue} that will use a linked data structure and supports {@link Queue#remove(Object)}.
     */
    public static <T> Queue<T> newUnboundedLinkedMpscQueue() {
        return Queues.newUnboundedLinkedMpscQueue();
    }

    /**
     * Create a new {@link Queue} which is safe to use for single producer (one thread!) and a single
     * consumer (one thread!).
     *
     * @param maxCapacity of the queue.
     * @param <T> Type of items stored in the queue.
     * @return A new SPSC {@link Queue} with max capacity of {@code maxCapacity}.
     */
    public static <T> Queue<T> newSpscQueue(final int maxCapacity) {
        return newSpscQueue(QUEUE_CHUNK_SIZE, maxCapacity);
    }

    /**
     * Create a new {@link Queue} which is safe to use for single producer (one thread!) and a single
     * consumer (one thread!).
     *
     * @param initialCapacity Initial capacity for the queue.
     * @param maxCapacity of the queue.
     * @param <T> Type of items stored in the queue.
     * @return A new SPSC {@link Queue} with max capacity of {@code maxCapacity}.
     */
    public static <T> Queue<T> newSpscQueue(final int initialCapacity, final int maxCapacity) {
        return Queues.newSpscQueue(initialCapacity, maxCapacity);
    }

    /**
     * Create a new unbounded {@link Queue} which is safe to use for single producer (one thread!) and a single
     * consumer (one thread!).
     *
     * @param initialCapacity of the returned queue.
     * @param <T> Type of items stored in the queue.
     * @return A new unbounded SPSC {@link Queue}.
     */
    public static <T> Queue<T> newUnboundedSpscQueue(final int initialCapacity) {
        return Queues.newUnboundedSpscQueue(initialCapacity);
    }

    private static final class Queues {
        private static final boolean USE_UNSAFE_QUEUES;
        private static final boolean USE_UNPADDED_QUEUES;

        static {
            // Internal property, subject to removal after validation completed.
            final String useUnpaddedQueuesName = "io.servicetalk.internal.queues.useUnpadded";
            boolean useUnpaddedQueues = parseBoolean(getProperty(useUnpaddedQueuesName, "false"));
            if (useUnpaddedQueues) {
                Queue<Integer> queue = null;
                try {
                    // Queues were added in JCTools 4.x. Add a defensive check in-case there are class path conflicts
                    // resulting in older library on the classpath. This can be removed after some time after folks
                    // have upgraded.
                    queue = new MpscLinkedUnpaddedQueue<>();
                } catch (Throwable ignored) {
                    useUnpaddedQueues = false;
                }
                LOGGER.debug("jctools unpadded: {}available.", queue == null ? "un" : "");
            }
            LOGGER.debug("{}: {}", useUnpaddedQueuesName, useUnpaddedQueues);
            USE_UNPADDED_QUEUES = useUnpaddedQueues;
        }

        private Queues() {
        }

        static {
            // jctools goes through its own process of initializing unsafe; of
            // course, this requires permissions which might not be granted to calling code, so we
            // must mark this block as privileged too
            Object maybeUnsafe = AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                // force JCTools to initialize unsafe
                try {
                    return UnsafeAccess.class.getDeclaredField("UNSAFE").get(null);
                } catch (IllegalAccessException | NoSuchFieldException e) {
                    return e;
                }
            });

            if (maybeUnsafe instanceof Throwable) {
                LOGGER.debug("jctools Unbounded/ChunkedArrayQueue: unavailable.", (Throwable) maybeUnsafe);
                USE_UNSAFE_QUEUES = false;
            } else {
                LOGGER.debug("jctools Unbounded/ChunkedArrayQueue: available.");
                USE_UNSAFE_QUEUES = true;
            }
        }

        static <T> Queue<T> newMpscQueue(final int initialCapacity, final int maxCapacity) {
            // Calculate the max capacity which can not be bigger then MAX_ALLOWED_MPSC_CAPACITY.
            // This is forced by the MpscChunkedArrayQueue implementation as will try to round it
            // up to the next power of two and so will overflow otherwise.
            final int initialCap = max(MIN_ALLOWED_MPSC_CHUNK_SIZE, initialCapacity);
            final int capacity = max(min(maxCapacity, MAX_ALLOWED_QUEUE_CAPACITY), MIN_MAX_MPSC_CAPACITY);
            return USE_UNSAFE_QUEUES ?
                    USE_UNPADDED_QUEUES ?
                        new MpscChunkedUnpaddedArrayQueue<>(initialCap, capacity) :
                        new MpscChunkedArrayQueue<>(initialCap, capacity)
                    : new MpscGrowableAtomicArrayQueue<>(initialCap, capacity);
        }

        static <T> Queue<T> newUnboundedMpscQueue(final int initialCapacity) {
            return USE_UNSAFE_QUEUES ?
                    USE_UNPADDED_QUEUES ?
                        new MpscUnboundedUnpaddedArrayQueue<>(max(MIN_ALLOWED_MPSC_CHUNK_SIZE, initialCapacity)) :
                        new MpscUnboundedArrayQueue<>(max(MIN_ALLOWED_MPSC_CHUNK_SIZE, initialCapacity))
                    : new MpscUnboundedAtomicArrayQueue<>(max(MIN_ALLOWED_MPSC_CHUNK_SIZE, initialCapacity));
        }

        static <T> Queue<T> newUnboundedLinkedMpscQueue() {
            return USE_UNSAFE_QUEUES ?
                    USE_UNPADDED_QUEUES ?
                        new MpscLinkedUnpaddedQueue<>() :
                        new MpscLinkedQueue<>()
                    : new MpscLinkedAtomicQueue<>();
        }

        static <T> Queue<T> newSpscQueue(final int initialCapacity, final int maxCapacity) {
            // Calculate the max capacity which can not be bigger then MAX_ALLOWED_MPSC_CAPACITY.
            // This is forced by the SpscChunkedArrayQueue implementation as will try to round it
            // up to the next power of two and so will overflow otherwise.
            final int initialCap = max(MIN_ALLOWED_SPSC_CHUNK_SIZE, initialCapacity);
            final int capacity = max(min(maxCapacity, MAX_ALLOWED_QUEUE_CAPACITY), MIN_MAX_SPSC_CAPACITY);
            return USE_UNSAFE_QUEUES ?
                    USE_UNPADDED_QUEUES ?
                        new SpscChunkedUnpaddedArrayQueue<>(initialCap, capacity) :
                        new SpscChunkedArrayQueue<>(initialCap, capacity)
                    : new SpscGrowableAtomicArrayQueue<>(initialCap, capacity);
        }

        static <T> Queue<T> newUnboundedSpscQueue(final int initialCapacity) {
            return USE_UNSAFE_QUEUES ?
                    USE_UNPADDED_QUEUES ?
                        new SpscUnboundedUnpaddedArrayQueue<>(initialCapacity) :
                        new SpscUnboundedArrayQueue<>(initialCapacity)
                    : new SpscUnboundedAtomicArrayQueue<>(initialCapacity);
        }
    }
}
