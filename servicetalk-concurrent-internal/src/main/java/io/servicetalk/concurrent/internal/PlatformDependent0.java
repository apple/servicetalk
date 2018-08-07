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

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;
import javax.annotation.Nullable;

import static java.lang.Boolean.getBoolean;
import static java.util.Objects.requireNonNull;

/**
 * {@link PlatformDependent} operations that require access to {@code sun.misc.*}.
 *
 * This class is forked from the netty project and modified to suit our needs.
 */
final class PlatformDependent0 {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformDependent0.class);

    private static final String DISABLE_UNSAFE_PROPERTY = "io.servicetalk.noUnsafe";

    private static final boolean isExplicitNoUnsafe = isExplicitNoUnsafe();
    @Nullable
    static final Unsafe UNSAFE;

    static {
        Unsafe unsafe;

        if (isExplicitNoUnsafe) {
            unsafe = null;
        } else {
            // attempt to access field Unsafe#theUnsafe
            final Object maybeUnsafe = AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                try {
                    final Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
                    Throwable cause = ReflectionUtil.trySetAccessible(unsafeField);
                    if (cause != null) {
                        return cause;
                    }
                    // the unsafe instance
                    return unsafeField.get(null);
                } catch (NoSuchFieldException | SecurityException | IllegalAccessException e) {
                    return e;
                }
            });

            // the conditional check here can not be replaced with checking that maybeUnsafe
            // is an instanceof Unsafe and reversing the if and else blocks; this is because an
            // instanceof check against Unsafe will trigger a class load and we might not have
            // the runtime permission accessClassInPackage.sun.misc
            if (maybeUnsafe instanceof Exception) {
                unsafe = null;
                LOGGER.debug("sun.misc.Unsafe.theUnsafe: unavailable", (Exception) maybeUnsafe);
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
                    } catch (NoSuchMethodException | SecurityException e) {
                        return e;
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
    }

    private PlatformDependent0() {
        // no instantiation
    }

    private static boolean isExplicitNoUnsafe() {
        return getBoolean(DISABLE_UNSAFE_PROPERTY);
    }

    /**
     * If {@code sun.misc.Unsafe} is available and has not been disabled.
     * @return {@code true} if {@code sun.misc.Unsafe} is available.
     */
    public static boolean hasUnsafe() {
        return UNSAFE != null;
    }

    static void throwException(Throwable cause) {
        // JVM has been observed to crash when passing a null argument. See https://github.com/netty/netty/issues/4131.
        UNSAFE.throwException(requireNonNull(cause));
    }
}
