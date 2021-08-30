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
package io.servicetalk.examples.http.opentracing;

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.opentracing.http.TracingHttpServiceFilter;
import io.servicetalk.opentracing.log4j2.ServiceTalkTracingThreadContextMap;
import io.servicetalk.opentracing.zipkin.publisher.reporter.HttpReporter;

import brave.Tracing;
import brave.opentracing.BraveTracer;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import io.opentracing.ScopeManager;
import io.opentracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import zipkin2.reporter.brave.ZipkinSpanHandler;

import java.net.InetSocketAddress;
import javax.annotation.Nullable;

import static io.opentracing.propagation.Format.Builtin.TEXT_MAP;
import static io.servicetalk.concurrent.api.AsyncContextMap.Key.newKey;

/**
 * A server that does distributed tracing via {@link BraveTracer}.
 */
public final class BraveTracingServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(BraveTracingServer.class);

    public static void main(String[] args) throws Exception {
        // Publishing to Zipkin is optional, but demonstrated for completeness.
        final InetSocketAddress bindAddress = new InetSocketAddress(8080);
        final String serviceName = "servicetalk-test-braveserver";
        try (Tracing tracing = Tracing.newBuilder()
                .localServiceName(serviceName)
                .localIp(bindAddress.getHostString())
                .localPort(bindAddress.getPort())
                .addSpanHandler(ZipkinSpanHandler.create(
                        new HttpReporter.Builder(HttpClients.forSingleAddress("localhost", 8081)).build()))
                .currentTraceContext(AsyncContextTraceContext.INSTANCE)
                .build();
             Tracer tracer = BraveTracer.newBuilder(tracing).build()) {
            HttpServers.forAddress(bindAddress)
                    .appendServiceFilter(new TracingHttpServiceFilter(tracer, TEXT_MAP, serviceName))
                    .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                        LOGGER.info("processed request {}", request.toString((name, value) -> value));
                        return responseFactory.ok();
                    }).awaitShutdown();
        }
    }

    /**
     * Brave doesn't support injecting a {@link ScopeManager} so {@link MDC} doesn't reflect trace info. If {@link MDC}
     * support is required with Brave, an approach similar to {@link ServiceTalkTracingThreadContextMap} could be taken
     * leveraging {@link AsyncContextTraceContext}.
     */
    private static final class AsyncContextTraceContext extends CurrentTraceContext {
        private static final AsyncContextMap.Key<TraceContext> SCOPE_KEY = newKey("bravetracing");
        public static final CurrentTraceContext INSTANCE = new AsyncContextTraceContext();

        private AsyncContextTraceContext() {
        }

        @Nullable
        @Override
        public TraceContext get() {
            return AsyncContext.get(SCOPE_KEY);
        }

        @Override
        public Scope newScope(final TraceContext context) {
            AsyncContextMap contextMap = AsyncContext.current();
            TraceContext previous = contextMap.get(SCOPE_KEY);
            contextMap.put(SCOPE_KEY, context);
            return decorateScope(context,
                    previous == null ? RemoveScopeOnClose.INSTANCE : new RevertScopeOnClose(previous));
        }

        private static final class RemoveScopeOnClose implements Scope {
            private static final Scope INSTANCE = new RemoveScopeOnClose();

            private RemoveScopeOnClose() {
            }

            @Override
            public void close() {
                AsyncContext.remove(SCOPE_KEY);
            }
        }

        private static final class RevertScopeOnClose implements Scope {
            private final TraceContext previousScope;

            RevertScopeOnClose(TraceContext previous) {
                this.previousScope = previous;
            }

            @Override
            public void close() {
                AsyncContext.put(SCOPE_KEY, previousScope);
            }
        }
    }
}
