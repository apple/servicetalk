/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.buffer.netty;

import io.netty.util.internal.ReflectionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Unsafe;

import java.io.FileDescriptor;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import javax.annotation.Nullable;

import static java.lang.Boolean.getBoolean;
import static java.util.Objects.requireNonNull;

/**
 * The {@link PlatformDependent0} operations which requires access to {@code sun.misc.*}.
 *
 * This class is forked from the netty project and modified to suit our needs.
 */
final class PlatformDependent0 {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformDependent0.class);

    private static final String DISABLE_UNSAFE_PROPERTY = "io.servicetalk.noUnsafe";

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

        // Define UNSAFE:
        if (isExplicitNoUnsafe()) {
            direct = null;
            unsafe = null;
        } else {
            direct = ByteBuffer.allocateDirect(1);

            // attempt to access field Unsafe#theUnsafe
            final Object maybeUnsafe = AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                try {
                    final Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
                    // We always want to try using Unsafe as the access still works on java9 as well and
                    // we need it for out native-transports and many optimizations.
                    Throwable cause = ReflectionUtil.trySetAccessible(unsafeField, false);
                    if (cause != null) {
                        return cause;
                    }
                    // the unsafe instance
                    return unsafeField.get(null);
                } catch (Throwable e) {
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
                unsafe = (Unsafe) maybeUnsafe;
                LOGGER.debug("sun.misc.Unsafe.theUnsafe: available");
            }
        }
        UNSAFE = unsafe;

        Lookup lookup = null;
        // Define DIRECT_BUFFER_CONSTRUCTOR:
        if (UNSAFE == null) {
            DIRECT_BUFFER_CONSTRUCTOR = null;
        } else {
            final Object maybeDirectBufferConstructor =
                    AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                        try {
                            final Constructor<?> constructor = direct.getClass().getDeclaredConstructor(
                                    int.class, long.class, FileDescriptor.class, Runnable.class);
                            Throwable cause = ReflectionUtil.trySetAccessible(constructor, true);
                            if (cause != null) {
                                return cause;
                            }
                            return constructor;
                        } catch (Throwable e) {
                            return e;
                        }
                    });

            MethodHandle directBufferConstructor;
            if (maybeDirectBufferConstructor instanceof Constructor<?>) {
                long address = -1;
                try {
                    lookup = MethodHandles.lookup();
                    directBufferConstructor = lookup.unreflectConstructor(
                            (Constructor<?>) maybeDirectBufferConstructor);
                    // try to use the constructor now
                    address = allocateMemory(1);
                    @SuppressWarnings("PMD.UnusedLocalVariable")
                    ByteBuffer buffer = (ByteBuffer) directBufferConstructor
                            .invoke(1, address, null, (Runnable) () -> { /* NOOP */ });
                } catch (Throwable throwable) {
                    directBufferConstructor = null;
                } finally {
                    if (address != -1) {
                        freeMemory(address);
                    }
                }
            } else {
                directBufferConstructor = null;
            }
            DIRECT_BUFFER_CONSTRUCTOR = directBufferConstructor;
        }
        LOGGER.debug("java.nio.DirectByteBuffer.<init>(int, long, FileDescriptor, Runnable): {}",
                DIRECT_BUFFER_CONSTRUCTOR != null ? "available" : "unavailable");

        // Define DEALLOCATOR_CONSTRUCTOR:
        if (UNSAFE == null || DIRECT_BUFFER_CONSTRUCTOR == null) {
            DEALLOCATOR_CONSTRUCTOR = null;
        } else {
            final Object maybeDeallocatorConstructor =
                    AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                        try {
                            for (Class<?> innerClass : direct.getClass().getDeclaredClasses()) {
                                if (DEALLOCATOR_CLASS_NAME.equals(innerClass.getName())) {
                                    final Constructor<?> constructor = innerClass.getDeclaredConstructor(
                                            long.class, long.class, int.class);
                                    Throwable cause = ReflectionUtil.trySetAccessible(constructor, true);
                                    if (cause != null) {
                                        return cause;
                                    }
                                    return constructor;
                                }
                            }
                            return new ClassNotFoundException(DEALLOCATOR_CLASS_NAME);
                        } catch (Throwable e) {
                            return e;
                        }
                    });

            MethodHandle deallocatorConstructor;
            if (maybeDeallocatorConstructor instanceof Constructor<?>) {
                try {
                    deallocatorConstructor = lookup.unreflectConstructor((Constructor<?>) maybeDeallocatorConstructor);
                    // try to use the constructor now
                    @SuppressWarnings("PMD.UnusedLocalVariable")
                    Runnable deallocator = (Runnable) deallocatorConstructor.invoke(0L, 0L, 0);
                } catch (Throwable throwable) {
                    deallocatorConstructor = null;
                }
            } else {
                deallocatorConstructor = null;
            }
            DEALLOCATOR_CONSTRUCTOR = deallocatorConstructor;
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

        USE_DIRECT_BUFFER_WITHOUT_ZEROING = UNSAFE != null && DIRECT_BUFFER_CONSTRUCTOR != null &&
                DEALLOCATOR_CONSTRUCTOR != null && RESERVE_MEMORY != null && UNRESERVE_MEMORY != null;
        LOGGER.debug("Allocation of DirectByteBuffer without zeroing memory: {}",
                USE_DIRECT_BUFFER_WITHOUT_ZEROING ? "available" : "unavailable");
    }

    private PlatformDependent0() {
        // No instances
    }

    @Nullable
    private static MethodHandle extractNioBitsMethod(final String methodName, final Lookup lookup) {
        final Object maybeMethod = AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
            try {
                final Class<?> bitsClass = Class.forName("java.nio.Bits", false, getSystemClassLoader());
                final Method reserveMemoryMethod = bitsClass.getDeclaredMethod(methodName, long.class, int.class);
                final Throwable cause = ReflectionUtil.trySetAccessible(reserveMemoryMethod, true);
                if (cause != null) {
                    return cause;
                }
                return reserveMemoryMethod;
            } catch (Throwable e) {
                return e;
            }
        });

        if (!(maybeMethod instanceof Method)) {
            return null;
        }

        try {
            final MethodHandle methodHandle = lookup.unreflect((Method) maybeMethod);
            methodHandle.invoke(1L, 1);
            return methodHandle;
        } catch (Throwable e) {
            return null;
        }
    }

    private static ClassLoader getSystemClassLoader() {
        if (System.getSecurityManager() == null) {
            return ClassLoader.getSystemClassLoader();
        } else {
            return AccessController.doPrivileged((PrivilegedAction<ClassLoader>) ClassLoader::getSystemClassLoader);
        }
    }

    private static boolean isExplicitNoUnsafe() {
        return getBoolean(DISABLE_UNSAFE_PROPERTY);
    }

    static boolean useDirectBufferWithoutZeroing() {
        return USE_DIRECT_BUFFER_WITHOUT_ZEROING;
    }

    static void reserveMemory(final long size, final int capacity) {
        assert RESERVE_MEMORY != null;
        try {
            RESERVE_MEMORY.invoke(size, capacity);
        } catch (Throwable e) {
            throw new Error(e);
        }
    }

    static void unreserveMemory(final long size, final int capacity) {
        assert UNRESERVE_MEMORY != null;
        try {
            UNRESERVE_MEMORY.invoke(size, capacity);
        } catch (Throwable e) {
            throw new Error(e);
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

    /**
     * Raises an exception bypassing compiler checks for checked exceptions.
     */
    static void throwException(final Throwable t) {
        if (UNSAFE != null) {
            throwExceptionUnsafe(t);
        } else {
            throwException0(t);
        }
    }

    private static void throwExceptionUnsafe(final Throwable cause) {
        // JVM has been observed to crash when passing a null argument. See https://github.com/netty/netty/issues/4131.
        assert UNSAFE != null;
        UNSAFE.throwException(requireNonNull(cause, "cause"));
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwException0(final Throwable t) throws E {
        throw (E) t;
    }
}
