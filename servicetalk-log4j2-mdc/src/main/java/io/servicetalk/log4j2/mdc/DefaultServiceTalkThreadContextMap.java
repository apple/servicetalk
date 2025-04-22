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
package io.servicetalk.log4j2.mdc;

import io.servicetalk.log4j2.mdc.utils.ServiceTalkThreadContextMap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Subclass which uses the {@link io.servicetalk.concurrent.api.CapturedContext} mechanism for propagation.
 */
public class DefaultServiceTalkThreadContextMap extends ServiceTalkThreadContextMap {

    private static final String ENABLE_PROPERTY_NAME = "io.servicetalk.log4j2.mdc.capturedContextStorage";
    private static final boolean DEFAULT_PROPERTY_VALUE = true;
    static final ThreadLocal<ConcurrentMap<String, String>> CONTEXT_STORAGE =
            ThreadLocal.withInitial(() ->
                // better be thread safe, since the context may be used in multiple operators which may use different
                // threads MDC is typically small (e.g. <8) so start with 4 (which ConcurrentHashMap will double to 8).
                new ConcurrentHashMap<>(4));

    final boolean useLocalStorage;

    /**
     * Create a new ServiceTalk based ThreadContextMap.
     * Note: this is only intended to be used by service loading mechanisms and not instantiated by user code.
     */
    public DefaultServiceTalkThreadContextMap() {
        useLocalStorage = useLocalStorage();
    }

    @Override
    protected final Map<String, String> getStorage() {
        return useLocalStorage ? CONTEXT_STORAGE.get() : super.getStorage();
    }

    private static boolean useLocalStorage() {
        String propertyValue = System.getProperty(ENABLE_PROPERTY_NAME);
        return propertyValue == null ? DEFAULT_PROPERTY_VALUE : Boolean.parseBoolean(propertyValue);
    }
}
