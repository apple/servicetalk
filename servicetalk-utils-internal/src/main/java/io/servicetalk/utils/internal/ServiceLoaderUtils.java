/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.utils.internal;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;

/**
 * {@link ServiceLoader} utilities.
 */
public final class ServiceLoaderUtils {

    private ServiceLoaderUtils() {
        // No instances.
    }

    /**
     * Loads provider classes via {@link ServiceLoader}.
     *
     * @param clazz interface of abstract class which implementations should be loaded
     * @param classLoader {@link ClassLoader} to use
     * @param logger {@link Logger} to use
     * @param <T> type of the provider
     * @return a list of loaded providers for the specified class
     */
    public static <T> List<T> loadProviders(final Class<T> clazz, final ClassLoader classLoader, final Logger logger) {
        final List<T> list = new ArrayList<>(0);
        for (T provider : ServiceLoader.load(clazz, classLoader)) {
            list.add(provider);
        }
        logger.debug("Registered {} {}(s): {}", list.size(), clazz.getSimpleName(), list);
        return list.isEmpty() ? emptyList() : unmodifiableList(list);
    }
}
