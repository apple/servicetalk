/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;

final class CapturedContextProviders {

    private static final Logger LOGGER = LoggerFactory.getLogger(CapturedContextProviders.class);
    private static final List<CapturedContextProvider> PROVIDERS;

    static {
        final ClassLoader classLoader = CapturedContextProviders.class.getClassLoader();
        PROVIDERS = loadProviders(CapturedContextProvider.class, classLoader, LOGGER);
    }

    private CapturedContextProviders() {
        // no instances
    }

    static List<CapturedContextProvider> providers() {
        return PROVIDERS;
    }

    // TODO: this was copied from `ServiceLoaderUtils` because the `CapturedContextProvider` interface is package
    //  private and the call to `ServiceLoader.load(..)` can only load implementations for types that are visible
    //  to the package from which it is called (because it checks if it's accessible by checking stack trace).
    //  One we make CapturedContextProvider public we can return to using ServiceLoaderUtils.
    private static <T> List<T> loadProviders(final Class<T> clazz, final ClassLoader classLoader, final Logger logger) {
        final List<T> list = new ArrayList<>(0);
        for (T provider : ServiceLoader.load(clazz, classLoader)) {
            list.add(provider);
        }
        if (list.isEmpty()) {
            logger.debug("ServiceLoader {}(s) registered: []", clazz.getSimpleName());
            return emptyList();
        }
        logger.info("ServiceLoader {}(s) registered: {}", clazz.getSimpleName(), list);
        return unmodifiableList(list);
    }
}
