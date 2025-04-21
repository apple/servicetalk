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

/**
 * Subclass which uses the {@link io.servicetalk.concurrent.api.CapturedContext} mechanism for propagation.
 */
public class DefaultServiceTalkThreadContextMap extends ServiceTalkThreadContextMap {

    static final ThreadLocal<Map<String, String>> CONTEXT_STORAGE =
            ThreadLocal.withInitial(() ->
                // better be thread safe, since the context may be used in multiple operators which may use different
                // threads MDC is typically small (e.g. <8) so start with 4 (which ConcurrentHashMap will double to 8).
                new ConcurrentHashMap<>(4));

    @Override
    protected Map<String, String> getStorage() {
        return CONTEXT_STORAGE.get();
    }
}
