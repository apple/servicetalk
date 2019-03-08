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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.transport.api.DelegatingExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;

final class ExecutionContextOverridingServiceContext extends DelegatingHttpServiceContext {
    private final ExecutionContext context;

    ExecutionContextOverridingServiceContext(final HttpServiceContext ctx, final Executor e) {
        super(ctx);
        context = new DelegatingExecutionContext(ctx.executionContext()) {
            @Override
            public Executor executor() {
                return e;
            }
        };
    }

    @Override
    public ExecutionContext executionContext() {
        return context;
    }
}
