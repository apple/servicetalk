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
 * Copyright 2013 The Netty Project
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileDescriptor;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.servicetalk.utils.internal.ReflectionUtils.extractNioBitsMethod;
import static io.servicetalk.utils.internal.ReflectionUtils.lookupAccessibleObject;
import static java.lang.Boolean.getBoolean;

/**
 * {@link PlatformDependent} operations that require access to {@code sun.misc.*}.
 * <p/>
 * This class is forked from the netty project and modified to suit our needs.
 */
final class PlatformDependent0 {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformDependent0.class);

    private static final boolean IS_EXPLICIT_NO_UNSAFE = getBoolean("io.servicetalk.noUnsafe");

    private static final String DEALLOCATOR_CLASS_NAME = "java.nio.DirectByteBuffer$Deallocator";

    @Nullable
    private static final Object UNSAFE;
    @Nullable
    private static final MethodHandle DIRECT_BUFFER_CONSTRUCTOR;
    @Nullable
    private static final MethodHandle DEALLOCATOR_CONSTRUCTOR;
    @Nullable
    private static final MethodHandle RESERVE_MEMORY;
    @Nullable
    private static final MethodHandle UNRESERVE_MEMORY;

    @Nullable
    private static final MethodHandle ALLOCATE_MEMORY;

    @Nullable
    private static final MethodHandle FREE_MEMORY;

    @Nullable
    private static final Consumer<Throwable> THROW_EXCEPTION;

    private static final boolean USE_DIRECT_BUFFER_WITHOUT_ZEROING;

    static {
        Object unsafe;

        if (IS_EXPLICIT_NO_UNSAFE) {
            unsafe = null;
        } else {
            // attempt to access field Unsafe#theUnsafe
            final Object maybeUnsafe = AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                try {
                    Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                    final Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
                    final Throwable cause = ReflectionUtils.trySetAccessible(unsafeField, false);
                    if (cause != null) {
                        return cause;
                    }
                    // the unsafe instance
                    return unsafeField.get(null);
                } catch (ClassNotFoundException | NoSuchFieldException | SecurityException | IllegalAccessException e) {
                    return e;
                }
            });

            // the conditional check here can not be replaced with checking that maybeUnsafe
            // is an instanceof Unsafe and reversing the if and else blocks; this is because an
            // instanceof check against Unsafe will trigger a class load and we might not have
            // the runtime permission accessClassInPackage.sun.misc
            if (maybeUnsafe instanceof Throwable) {
                unsafe = null;
                LOGGER.debug("sun.misc.Unsafe.theUnsafe: unavailable", (Throwable) maybeUnsafe);
            } else {
                unsafe = maybeUnsafe;
                LOGGER.debug("sun.misc.Unsafe.theUnsafe: {}", null == unsafe ? "unavailable" : "available");
            }
        }
        UNSAFE = unsafe;

        if (null == unsafe) {
            THROW_EXCEPTION = null;
            ALLOCATE_MEMORY = null;
            FREE_MEMORY = null;
        } else {
            Consumer<Throwable> throwConsumer = null;
            MethodHandle allocateMH = null;
            MethodHandle freeMH = null;
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandle throwExceptionMH = null;
            try {
                throwExceptionMH = lookup.findVirtual(unsafe.getClass(), "throwException",
                        MethodType.methodType(void.class, Throwable.class));
                CallSite throwExceptionCallSite = LambdaMetafactory.metafactory(
                        lookup, "accept",
                        MethodType.methodType(Consumer.class, unsafe.getClass()),
                        MethodType.methodType(void.class, Object.class),
                        throwExceptionMH,
                        MethodType.methodType(void.class, Throwable.class));
                throwConsumer = (Consumer<Throwable>) throwExceptionCallSite.getTarget().bindTo(unsafe).invoke();
            } catch (Throwable all) {
                LOGGER.warn("failed computing THROW_EXCEPTION", all);
            }
            try {
                allocateMH = lookup.findVirtual(unsafe.getClass(), "allocateMemory",
                        MethodType.methodType(long.class, long.class)).bindTo(unsafe);
            } catch (Throwable all) {
                LOGGER.warn("failed computing THROW_EXCEPTION", all);
            }
            try {
                freeMH = lookup.findVirtual(unsafe.getClass(), "freeMemory",
                        MethodType.methodType(void.class, long.class)).bindTo(unsafe);
            } catch (Throwable all) {
                LOGGER.warn("failed computing THROW_EXCEPTION", all);
            }
            THROW_EXCEPTION = throwConsumer;
            ALLOCATE_MEMORY = allocateMH;
            FREE_MEMORY = freeMH;
        }

        final ByteBuffer direct;
        final MethodHandles.Lookup lookup;
        // Define DIRECT_BUFFER_CONSTRUCTOR:
        if (UNSAFE == null) {
            direct = null;
            lookup = null;
            DIRECT_BUFFER_CONSTRUCTOR = null;
        } else {
            direct = ByteBuffer.allocateDirect(1);
            lookup = MethodHandles.lookup();
            DIRECT_BUFFER_CONSTRUCTOR = lookupAccessibleObject(() -> direct.getClass().getDeclaredConstructor(
                    int.class, long.class, FileDescriptor.class, Runnable.class), Constructor.class, constructor -> {

                long address = 0L;
                try {
                    final MethodHandle methodHandle = lookup.unreflectConstructor(constructor);
                    address = allocateMemory(1);
                    if (methodHandle.invoke(1, address, null, (Runnable) () -> { /* NOOP */ }) instanceof ByteBuffer) {
                        return methodHandle;
                    }
                    return null;
                } finally {
                    if (address != 0L) {
                        freeMemory(address);
                    }
                }
            });
        }
        LOGGER.debug("java.nio.DirectByteBuffer.<init>(int, long, FileDescriptor, Runnable): {}",
                DIRECT_BUFFER_CONSTRUCTOR != null ? "available" : "unavailable");

        // Define DEALLOCATOR_CONSTRUCTOR:
        if (UNSAFE == null || DIRECT_BUFFER_CONSTRUCTOR == null) {
            DEALLOCATOR_CONSTRUCTOR = null;
        } else {
            DEALLOCATOR_CONSTRUCTOR = lookupAccessibleObject(() -> {
                for (Class<?> innerClass : direct.getClass().getDeclaredClasses()) {
                    if (DEALLOCATOR_CLASS_NAME.equals(innerClass.getName())) {
                        return innerClass.getDeclaredConstructor(long.class, long.class, int.class);
                    }
                }
                return null;
            }, Constructor.class, constructor -> {
                final MethodHandle methodHandle = lookup.unreflectConstructor(constructor);
                if (methodHandle.invoke(0L, 0L, 0) instanceof Runnable) {
                    return methodHandle;
                }
                return null;
            });
        }
        LOGGER.debug("java.nio.DirectByteBuffer$Deallocator.<init>(long, long, int): {}",
                DIRECT_BUFFER_CONSTRUCTOR != null ? "available" : "unavailable");

        // Define RESERVE_MEMORY and UNRESERVE_MEMORY:
        if (UNSAFE == null || DIRECT_BUFFER_CONSTRUCTOR == null || DEALLOCATOR_CONSTRUCTOR == null) {
            RESERVE_MEMORY = null;
            UNRESERVE_MEMORY = null;
        } else {
            RESERVE_MEMORY = extractNioBitsMethod("reserveMemory", lookup);
            UNRESERVE_MEMORY = extractNioBitsMethod("unreserveMemory", lookup);
        }
        LOGGER.debug("java.nio.Bits.reserveMemory(long, int): {}",
                RESERVE_MEMORY != null ? "available" : "unavailable");
        LOGGER.debug("java.nio.Bits.unreserveMemory(long, int): {}",
                UNRESERVE_MEMORY != null ? "available" : "unavailable");

        // Define USE_DIRECT_BUFFER_WITHOUT_ZEROING:
        USE_DIRECT_BUFFER_WITHOUT_ZEROING = UNSAFE != null && DIRECT_BUFFER_CONSTRUCTOR != null &&
                DEALLOCATOR_CONSTRUCTOR != null && RESERVE_MEMORY != null && UNRESERVE_MEMORY != null;
        LOGGER.debug("Allocation of DirectByteBuffer without zeroing memory: {}",
                USE_DIRECT_BUFFER_WITHOUT_ZEROING ? "available" : "unavailable");
    }

    private PlatformDependent0() {
        // no instantiation
    }

    static boolean hasUnsafe() {
        return UNSAFE != null;
    }

    static boolean useDirectBufferWithoutZeroing() {
        return USE_DIRECT_BUFFER_WITHOUT_ZEROING;
    }

    static void reserveMemory(final long size, final int capacity) {
        assert RESERVE_MEMORY != null;
        try {
            RESERVE_MEMORY.invoke(size, capacity);
        } catch (Throwable t) {
            throw new Error(t);
        }
    }

    static void unreserveMemory(final long size, final int capacity) {
        assert UNRESERVE_MEMORY != null;
        try {
            UNRESERVE_MEMORY.invoke(size, capacity);
        } catch (Throwable t) {
            throw new Error(t);
        }
    }

    static long allocateMemory(final long size) {
        assert UNSAFE != null;
        try {
            return (long) ALLOCATE_MEMORY.invokeExact(size);
        } catch (Throwable e) {
            throwException(e);
            return Long.MIN_VALUE; // never reached
        }
    }

    static void freeMemory(final long address) {
        assert UNSAFE != null;
        try {
            FREE_MEMORY.invokeExact(address);
        } catch (Throwable e) {
            throwException(e);
        }
    }

    static ByteBuffer newDirectBuffer(final long address, final long size, final int capacity) {
        assert DEALLOCATOR_CONSTRUCTOR != null;
        assert DIRECT_BUFFER_CONSTRUCTOR != null;
        try {
            final Runnable deallocator = (Runnable) DEALLOCATOR_CONSTRUCTOR.invoke(address, size, capacity);
            return (ByteBuffer) DIRECT_BUFFER_CONSTRUCTOR.invoke(capacity, address, null, deallocator);
        } catch (Throwable cause) {
            // Not expected to ever throw!
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new Error(cause);
        }
    }

    static void throwException(Throwable cause) {
        assert UNSAFE != null;
        // JVM has been observed to crash when passing a null argument. See https://github.com/netty/netty/issues/4131.
        try {
            THROW_EXCEPTION.accept(Objects.requireNonNull(cause));
        } catch (Throwable all) {
            sneakyThrows(cause);
        }
    }

    private static <E extends Throwable> void sneakyThrows(final Throwable t) throws E {
        throw (E) t;
    }
}
