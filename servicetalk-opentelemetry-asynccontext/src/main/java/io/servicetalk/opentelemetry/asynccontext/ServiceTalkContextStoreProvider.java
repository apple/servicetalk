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

package io.servicetalk.opentelemetry.asynccontext;

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.context.api.ContextMap;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.ContextStorageProvider;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import javax.annotation.Nullable;

import static io.servicetalk.context.api.ContextMap.Key.newKey;

/**
 * Implementation of {@link ContextStorageProvider} that stores the Tracing Context
 * making it available within {@link AsyncContext}.
 */
public final class ServiceTalkContextStoreProvider implements ContextStorageProvider {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final ContextMap.Key<Context> SCOPE_KEY = newKey("opentelemetry", Context.class);

    @Override
    public ContextStorage get() {
        return ServiceTalkContextStorage.INSTANCE;
    }

    private enum ServiceTalkContextStorage implements ContextStorage {
        INSTANCE;

        @Override
        public Scope attach(Context toAttach) {
            return attach(AsyncContext.context(), toAttach);
        }

        private Scope attach(ContextMap contextMap, @Nullable Context toAttach) {
            if (toAttach == null) {
                return Scope.noop();
            }

            final Context beforeAttach = current();
            if (beforeAttach == toAttach) {
                return Scope.noop();
            }
            contextMap.put(SCOPE_KEY, toAttach);

            return () -> {
                final Context current = current();
                if (current != toAttach) {
                    if (logger.isDebugEnabled()) {
                        logger.debug(
                            "Context {} in storage isn't the expected context {}, Scope wasn't closed correctly",
                            current, toAttach, new Throwable("stacktrace"));
                    } else {
                        logger.info(
                            "Context {} in storage isn't the expected context {}, Scope wasn't closed correctly",
                            current, toAttach);
                    }
                }
                if (beforeAttach == null) {
                    contextMap.remove(SCOPE_KEY);
                } else {
                    contextMap.put(SCOPE_KEY, beforeAttach);
                }
            };
        }

        @Nullable
        @Override
        public Context current() {
            return AsyncContext.context().get(SCOPE_KEY);
        }
    }
}
