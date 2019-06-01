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
package io.servicetalk.concurrent.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Unsafe;

import java.io.FileDescriptor;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.ReflectionUtils.extractNioBitsMethod;
import static io.servicetalk.concurrent.internal.ReflectionUtils.lookupConstructor;
import static java.lang.Boolean.getBoolean;
import static java.util.Objects.requireNonNull;

/**
 * {@link PlatformDependent} operations that require access to {@code sun.misc.*}.
 *
 * This class is forked from the netty project and modified to suit our needs.
 */
final class PlatformDependent0 {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformDependent0.class);

    private static final boolean IS_EXPLICIT_NO_UNSAFE = getBoolean("io.servicetalk.noUnsafe");

    private static final String DEALLOCATOR_CLASS_NAME = "java.nio.DirectByteBuffer$Deallocator";

    @Nullable
    private static final Unsafe UNSAFE;
    @Nullable
    private static final MethodHandle DIRECT_BUFFER_CONSTRUCTOR;
    @Nullable
    private static final MethodHandle DEALLOCATOR_CONSTRUCTOR;
    @Nullable
    private static final MethodHandle RESERVE_MEMORY;
    @Nullable
    private static final MethodHandle UNRESERVE_MEMORY;

    private static final boolean USE_DIRECT_BUFFER_WITHOUT_ZEROING;

    static {
        final ByteBuffer direct;
        Unsafe unsafe;

        if (IS_EXPLICIT_NO_UNSAFE) {
            direct = null;
            unsafe = null;
        } else {
            direct = ByteBuffer.allocateDirect(1);

            // attempt to access field Unsafe#theUnsafe
            final Object maybeUnsafe = AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                try {
                    final Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
                    final Throwable cause = ReflectionUtils.trySetAccessible(unsafeField, false);
                    if (cause != null) {
                        return cause;
                    }
                    // the unsafe instance
                    return unsafeField.get(null);
                } catch (Throwable t) {
                    return t;
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
                unsafe = (Unsafe) maybeUnsafe;
                LOGGER.debug("sun.misc.Unsafe.theUnsafe: available");
            }

            // ensure the unsafe supports all necessary methods to work around the mistake in the latest OpenJDK
            // http://www.mail-archive.com/jdk6-dev@openjdk.java.net/msg00698.html
            if (unsafe != null) {
                final Unsafe finalUnsafe = unsafe;
                final Object maybeException = AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                    try {
                        finalUnsafe.getClass().getDeclaredMethod(
                                "copyMemory", Object.class, long.class, Object.class, long.class, long.class);
                        return null;
                    } catch (Throwable t) {
                        return t;
                    }
                });

                if (maybeException == null) {
                    LOGGER.debug("sun.misc.Unsafe.copyMemory: available");
                } else {
                    // Unsafe.copyMemory(Object, long, Object, long, long) unavailable.
                    unsafe = null;
                    LOGGER.debug("sun.misc.Unsafe.copyMemory: unavailable", (Throwable) maybeException);
                }
            }
        }
        UNSAFE = unsafe;

        MethodHandles.Lookup lookup = null;
        // Define DIRECT_BUFFER_CONSTRUCTOR:
        if (UNSAFE == null) {
            DIRECT_BUFFER_CONSTRUCTOR = null;
        } else {
            lookup = MethodHandles.lookup();
            DIRECT_BUFFER_CONSTRUCTOR = lookupConstructor(() -> {
                try {
                    return direct.getClass().getDeclaredConstructor(
                            int.class, long.class, FileDescriptor.class, Runnable.class);
                } catch (Throwable t) {
                    return null;
                }
            }, lookup, methodHandle -> {
                long address = 0L;
                try {
                    address = allocateMemory(1);
                    return methodHandle.invoke(1, address, null, (Runnable) () -> { /* NOOP */ }) instanceof ByteBuffer;
                } catch (Throwable t) {
                    return false;
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
            DEALLOCATOR_CONSTRUCTOR = lookupConstructor(() -> {
                for (Class<?> innerClass : direct.getClass().getDeclaredClasses()) {
                    if (DEALLOCATOR_CLASS_NAME.equals(innerClass.getName())) {
                        try {
                            return innerClass.getDeclaredConstructor(long.class, long.class, int.class);
                        } catch (Throwable t) {
                            return null;
                        }
                    }
                }
                return null;
            }, lookup, methodHandle -> {
                try {
                    return methodHandle.invoke(0L, 0L, 0) instanceof Runnable;
                } catch (Throwable throwable) {
                    return false;
                }
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
        return UNSAFE.allocateMemory(size);
    }

    static void freeMemory(final long address) {
        assert UNSAFE != null;
        UNSAFE.freeMemory(address);
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
        // JVM has been observed to crash when passing a null argument. See https://github.com/netty/netty/issues/4131.
        UNSAFE.throwException(requireNonNull(cause));
    }
}
