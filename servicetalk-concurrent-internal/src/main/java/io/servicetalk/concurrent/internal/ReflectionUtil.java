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
 * Copyright 2017 The Netty Project
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

import java.lang.reflect.AccessibleObject;
import javax.annotation.Nullable;

import static java.lang.Boolean.parseBoolean;

/**
 * Provide utilities to assist reflective access.
 *
 * This class is forked from the netty project and modified to suit our needs.
 */
final class ReflectionUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReflectionUtil.class);

    private static final int JAVA_VERSION = javaVersion0();
    private static final boolean IS_EXPLICIT_TRY_REFLECTION_SET_ACCESSIBLE = explicitTryReflectionSetAccessible0();

    private ReflectionUtil() {
        // no instances
    }

    /**
     * Try to call {@link AccessibleObject#setAccessible(boolean)} but will catch any {@link SecurityException} and
     * {@link java.lang.reflect.InaccessibleObjectException} (for JDK 9) and return it.
     * The caller must check if it returns {@code null} and if not handle the returned exception.
     */
    @SuppressWarnings("JavadocReference")
    @Nullable
    static Throwable trySetAccessible(final AccessibleObject object, final boolean checkAccessible) {
        if (checkAccessible && !IS_EXPLICIT_TRY_REFLECTION_SET_ACCESSIBLE) {
            return new UnsupportedOperationException("Reflective setAccessible(true) disabled");
        }
        try {
            object.setAccessible(true);
            return null;
        } catch (SecurityException e) {
            return e;
        } catch (RuntimeException e) {
            return handleInaccessibleObjectException(e);
        }
    }

    private static RuntimeException handleInaccessibleObjectException(final RuntimeException e) {
        // JDK 9 can throw an inaccessible object exception here; since ServiceTalk compiles
        // against JDK 8 and this exception was only added in JDK 9, we have to weakly
        // check the type
        if ("java.lang.reflect.InaccessibleObjectException".equals(e.getClass().getName())) {
            return e;
        }
        throw e;
    }

    private static boolean explicitTryReflectionSetAccessible0() {
        return getBoolean("io.servicetalk.tryReflectionSetAccessible", JAVA_VERSION < 9);
    }

    static boolean getBoolean(final String name, final boolean def) {
        final String value = System.getProperty(name);
        if (value == null || value.isEmpty()) {
            return def;
        }

        boolean result = false;
        try {
            result = parseBoolean(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            // ignore
        }
        return result;
    }

    private static int javaVersion0() {
        final int majorVersion = isAndroid0() ? 6 : majorVersionFromJavaSpecificationVersion();
        LOGGER.debug("Java version: {}", majorVersion);
        return majorVersion;
    }

    private static boolean isAndroid0() {
        // Idea: Sometimes java binaries include Android classes on the classpath, even if it isn't actually Android.
        // Rather than check if certain classes are present, just check the VM, which is tied to the JDK.

        // Optional improvement: check if `android.os.Build.VERSION` is >= 24. On later versions of Android, the
        // OpenJDK is used, which means `Unsafe` will actually work as expected.

        // Android sets this property to Dalvik, regardless of whether it actually is.
        String vmName = System.getProperty("java.vm.name");
        boolean isAndroid = "Dalvik".equals(vmName);
        if (isAndroid) {
            LOGGER.debug("Platform: Android");
        }
        return isAndroid;
    }

    private static int majorVersionFromJavaSpecificationVersion() {
        return majorVersion(System.getProperty("java.specification.version", "1.6"));
    }

    private static int majorVersion(final String javaSpecVersion) {
        final String[] components = javaSpecVersion.split("\\.");
        final int[] version = new int[components.length];
        for (int i = 0; i < components.length; i++) {
            version[i] = Integer.parseInt(components[i]);
        }

        if (version[0] == 1) {
            assert version[1] >= 6;
            return version[1];
        } else {
            return version[0];
        }
    }
}
