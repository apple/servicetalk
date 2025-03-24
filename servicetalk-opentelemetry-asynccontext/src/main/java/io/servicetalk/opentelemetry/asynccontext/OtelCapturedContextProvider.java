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
package io.servicetalk.opentelemetry.asynccontext;

import io.servicetalk.concurrent.api.CapturedContext;
import io.servicetalk.concurrent.api.CapturedContextProvider;
import io.servicetalk.concurrent.api.Scope;
import io.servicetalk.context.api.ContextMap;

import io.opentelemetry.context.Context;

/**
 * A {@link CapturedContextProvider} that properly captures and restores the Open Telemetry {@link Context}.
 */
public final class OtelCapturedContextProvider implements CapturedContextProvider {

    /**
     * Creates a new instance.
     */
    public OtelCapturedContextProvider() {
    }

    @Override
    public CapturedContext captureContext(CapturedContext underlying) {
        return new WithOtelCapturedContext(Context.current(), underlying);
    }

    private static final class WithOtelCapturedContext implements CapturedContext {

        private final Context otelContext;
        private final CapturedContext stContext;

        WithOtelCapturedContext(Context otelContext, CapturedContext stContext) {
            this.otelContext = otelContext;
            this.stContext = stContext;
        }

        @Override
        public ContextMap captured() {
            return stContext.captured();
        }

        @Override
        public Scope attachContext() {
            Scope stScope = stContext.attachContext();
            io.opentelemetry.context.Scope otelScope = otelContext.makeCurrent();
            return () -> {
                otelScope.close();
                stScope.close();
            };
        }
    }
}
