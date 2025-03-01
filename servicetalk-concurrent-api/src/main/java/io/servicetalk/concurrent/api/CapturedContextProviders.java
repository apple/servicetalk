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
    private static final List<CapturedContextProvider> PROVIDERS;

    static {
        final ClassLoader classLoader = CapturedContextProviders.class.getClassLoader();
        List<CapturedContextProvider> providers = emptyList();
        try {
            providers = loadProviders(CapturedContextProvider.class, classLoader);
        } catch (Throwable ex) {
            runLater(() ->
                    getLogger().error("Failed to load {} instances", CapturedContextProvider.class.getName(), ex));
        }
        PROVIDERS = providers;
    }

    private CapturedContextProviders() {
        // no instances
    }

    static List<CapturedContextProvider> providers() {
        return PROVIDERS;
    }

    // This was copied from `ServiceLoaderUtils` because the implementation there logs the result, which normally
    // wouldn't be a problem but as it turns out things like the MDC logging utils depend on the AsyncContext which
    // may result in a cyclical dependency in class initialization.
    private static <T> List<T> loadProviders(final Class<T> clazz, final ClassLoader classLoader) {
        final List<T> list = new ArrayList<>(0);
        for (T provider : ServiceLoader.load(clazz, classLoader)) {
            list.add(provider);
        }
        if (list.isEmpty()) {
            runLater(() -> getLogger().debug("ServiceLoader {}(s) registered: []", clazz.getSimpleName()));
            return emptyList();
        }
        runLater(() -> getLogger().info("ServiceLoader {}(s) registered: {}", clazz.getSimpleName(), list));
        return unmodifiableList(list);
    }

    // We have to run logging code 'later' because of cyclical dependency issues that can arise if logging depends
    // on the AsyncContext.
    static void runLater(Runnable runnable) {
        Thread t = new Thread(runnable);
        t.setDaemon(true);
        t.setName(CapturedContextProviders.class.getSimpleName() + "-logging");
        t.start();
    }

    private static Logger getLogger() {
        return LoggerFactory.getLogger(CapturedContextProviders.class);
    }
}
