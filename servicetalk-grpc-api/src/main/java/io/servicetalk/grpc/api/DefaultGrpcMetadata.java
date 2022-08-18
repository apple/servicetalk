/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.api;

import io.servicetalk.concurrent.internal.DefaultContextMap;
import io.servicetalk.context.api.ContextMap;

import java.util.function.Supplier;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

class DefaultGrpcMetadata implements GrpcMetadata {

    private final String path;
    private final Supplier<ContextMap> requestContext;
    private final Supplier<ContextMap> responseContext;

    /**
     * Construct a new instance.
     *
     * @param path The path for the associated <a href="https://www.grpc.io">gRPC</a> method.
     * @param requestContext {@link Supplier} that returns the same {@link ContextMap} on each invocation.
     * @param responseContext {@link Supplier} that returns the same {@link ContextMap} on each invocation.
     */
    DefaultGrpcMetadata(final String path,
                        final Supplier<ContextMap> requestContext,
                        final Supplier<ContextMap> responseContext) {
        this.path = requireNonNull(path, "path");
        this.requestContext = requireNonNull(requestContext, "requestContext");
        this.responseContext = requireNonNull(responseContext, "responseContext");
    }

    @Deprecated
    @Override
    public String path() {
        return path;
    }

    @Override
    public ContextMap requestContext() {
        return requestContext.get();
    }

    @Override
    public ContextMap responseContext() {
        return responseContext.get();
    }

    boolean responseContext(final ContextMap context) {
        if (responseContext instanceof LazyContextMapSupplier) {
            return ((LazyContextMapSupplier) responseContext).initialize(context);
        }
        return false;
    }

    static final class LazyContextMapSupplier implements Supplier<ContextMap> {

        @Nullable
        private ContextMap context;

        @Override
        public ContextMap get() {
            if (context == null) {
                context = new DefaultContextMap();
            }
            return context;
        }

        boolean isInitialized() {
            return context != null;
        }

        boolean initialize(final ContextMap context) {
            if (!isInitialized()) {
                this.context = requireNonNull(context);
                return true;
            }
            return false;
        }
    }
}
