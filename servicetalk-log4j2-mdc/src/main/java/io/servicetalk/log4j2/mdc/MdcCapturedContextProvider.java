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
package io.servicetalk.log4j2.mdc;

import io.servicetalk.concurrent.api.CapturedContext;
import io.servicetalk.concurrent.api.CapturedContextProvider;
import io.servicetalk.concurrent.api.Scope;
import io.servicetalk.context.api.ContextMap;

import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.spi.ReadOnlyThreadContextMap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MdcCapturedContextProvider implements CapturedContextProvider {

    private final boolean enabled;

    public MdcCapturedContextProvider() {
        enabled = shouldEnableMdcCapture();
    }

    @Override
    public CapturedContext captureContext(CapturedContext underlying) {
        return enabled ? new MdcCapturedContext(underlying, getCurrent()) : underlying;
    }

    @Override
    public CapturedContext captureContextCopy(CapturedContext underlying) {
        return enabled ? new MdcCapturedContext(underlying, getCurrentCopy()) : underlying;
    }

    private static final class MdcCapturedContext implements CapturedContext {

        private final CapturedContext delegate;
        private final Map<String, String> storage;

        MdcCapturedContext(CapturedContext delegate, Map<String, String> storage) {
            this.delegate = delegate;
            this.storage = storage;
        }

        @Override
        public ContextMap captured() {
            return delegate.captured();
        }

        @Override
        public Scope attachContext() {
            Map<String, String> old = getCurrent();
            setCurrent(storage);
            Scope delegateScope = delegate.attachContext();
            return () -> {
                delegateScope.close();
                setCurrent(old);
            };
        }
    }

    private static Map<String, String> getCurrent() {
        return DefaultServiceTalkThreadContextMap.CONTEXT_STORAGE.get();
    }

    private static Map<String, String> getCurrentCopy() {
        Map<String, String> current = DefaultServiceTalkThreadContextMap.CONTEXT_STORAGE.get();
        Map<String, String> result = new ConcurrentHashMap<>(Math.max(current.size(), 4));
        result.putAll(current);
        return result;
    }

    private static void setCurrent(Map<String, String> storage) {
        DefaultServiceTalkThreadContextMap.CONTEXT_STORAGE.set(storage);
    }

    // TODO: we could attempt a 'slow path' where we can copy and propagate values over whatever MDC context provider
    //  is present, but for now we're just going to disable it since that would be what happens anyway.
    @SuppressWarnings({"UseOfSystemOutOrSystemErr", "PMD.SystemPrintln"})
    private static boolean shouldEnableMdcCapture() {
        ReadOnlyThreadContextMap implementation = ThreadContext.getThreadContextMap();
        if (implementation instanceof DefaultServiceTalkThreadContextMap) {
            return true;
        }
        System.err.println("Incompatible MDC ThreadContext adapter detected (" +
                implementation.getClass().getName() + "). ServiceTalk MDC propagation will be disabled.");
        return false;
    }
}
