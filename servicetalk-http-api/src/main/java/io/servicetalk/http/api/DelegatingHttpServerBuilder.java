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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.ConnectionAcceptorFactory;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerSslConfig;
import io.servicetalk.transport.api.TransportObserver;

import java.net.SocketOption;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * A {@link HttpServerBuilder} that delegates all methods to another {@link HttpServerBuilder}.
 */
public class DelegatingHttpServerBuilder implements HttpServerBuilder {

    private final HttpServerBuilder delegate;

    /**
     * Create a new instance.
     *
     * @param delegate {@link HttpServerBuilder} to which all methods are delegated.
     */
    public DelegatingHttpServerBuilder(final HttpServerBuilder delegate) {
        this.delegate = requireNonNull(delegate);
    }

    /**
     * Returns the {@link HttpServerBuilder} delegate.
     *
     * @return Delegate {@link HttpServerBuilder}.
     */
    protected final HttpServerBuilder delegate() {
        return delegate;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{delegate=" + delegate() + '}';
    }

    @Override
    public HttpServerBuilder protocols(final HttpProtocolConfig... protocols) {
        delegate.protocols(protocols);
        return this;
    }

    @Override
    public HttpServerBuilder sslConfig(final ServerSslConfig config) {
        delegate.sslConfig(config);
        return this;
    }

    @Override
    public HttpServerBuilder sslConfig(final ServerSslConfig defaultConfig, final Map<String, ServerSslConfig> sniMap) {
        delegate.sslConfig(defaultConfig, sniMap);
        return this;
    }

    @Override
    public <T> HttpServerBuilder socketOption(final SocketOption<T> option, final T value) {
        delegate.socketOption(option, value);
        return this;
    }

    @Override
    public <T> HttpServerBuilder listenSocketOption(final SocketOption<T> option, final T value) {
        delegate.listenSocketOption(option, value);
        return this;
    }

    @Override
    public HttpServerBuilder enableWireLogging(final String loggerName, final LogLevel logLevel,
                                               final BooleanSupplier logUserData) {
        delegate.enableWireLogging(loggerName, logLevel, logUserData);
        return this;
    }

    @Override
    public HttpServerBuilder transportObserver(final TransportObserver transportObserver) {
        delegate.transportObserver(transportObserver);
        return this;
    }

    @Override
    public HttpServerBuilder lifecycleObserver(final HttpLifecycleObserver lifecycleObserver) {
        delegate.lifecycleObserver(lifecycleObserver);
        return this;
    }

    @Override
    public HttpServerBuilder drainRequestPayloadBody(final boolean enable) {
        delegate.drainRequestPayloadBody(enable);
        return this;
    }

    @Override
    public HttpServerBuilder allowDropRequestTrailers(final boolean allowDrop) {
        delegate.allowDropRequestTrailers(allowDrop);
        return this;
    }

    @Override
    public HttpServerBuilder appendConnectionAcceptorFilter(final ConnectionAcceptorFactory factory) {
        delegate.appendConnectionAcceptorFilter(factory);
        return this;
    }

    @Override
    public HttpServerBuilder appendNonOffloadingServiceFilter(final StreamingHttpServiceFilterFactory factory) {
        delegate.appendNonOffloadingServiceFilter(factory);
        return this;
    }

    @Override
    public HttpServerBuilder appendNonOffloadingServiceFilter(final Predicate<StreamingHttpRequest> predicate,
                                                              final StreamingHttpServiceFilterFactory factory) {
        delegate.appendNonOffloadingServiceFilter(predicate, factory);
        return this;
    }

    @Override
    public HttpServerBuilder appendServiceFilter(final StreamingHttpServiceFilterFactory factory) {
        delegate.appendServiceFilter(factory);
        return this;
    }

    @Override
    public HttpServerBuilder appendServiceFilter(final Predicate<StreamingHttpRequest> predicate,
                                                 final StreamingHttpServiceFilterFactory factory) {
        delegate.appendServiceFilter(predicate, factory);
        return this;
    }

    @Override
    public HttpServerBuilder ioExecutor(final IoExecutor ioExecutor) {
        delegate.ioExecutor(ioExecutor);
        return this;
    }

    @Override
    public HttpServerBuilder executor(final Executor executor) {
        delegate.executor(executor);
        return this;
    }

    @Override
    public HttpServerBuilder bufferAllocator(final BufferAllocator allocator) {
        delegate.bufferAllocator(allocator);
        return this;
    }

    @Override
    public HttpServerBuilder executionStrategy(final HttpExecutionStrategy strategy) {
        delegate.executionStrategy(strategy);
        return this;
    }

    @Override
    public Single<HttpServerContext> listen(final HttpService service) {
        return delegate.listen(service);
    }

    @Override
    public Single<HttpServerContext> listenStreaming(final StreamingHttpService service) {
        return delegate.listenStreaming(service);
    }

    @Override
    public Single<HttpServerContext> listenBlocking(final BlockingHttpService service) {
        return delegate.listenBlocking(service);
    }

    @Override
    public Single<HttpServerContext> listenBlockingStreaming(final BlockingStreamingHttpService service) {
        return delegate.listenBlockingStreaming(service);
    }

    @Override
    public HttpServerContext listenAndAwait(final HttpService service) throws Exception {
        return delegate.listenAndAwait(service);
    }

    @Override
    public HttpServerContext listenStreamingAndAwait(final StreamingHttpService service) throws Exception {
        return delegate.listenStreamingAndAwait(service);
    }

    @Override
    public HttpServerContext listenBlockingAndAwait(final BlockingHttpService service) throws Exception {
        return delegate.listenBlockingAndAwait(service);
    }

    @Override
    public HttpServerContext listenBlockingStreamingAndAwait(final BlockingStreamingHttpService service)
            throws Exception {
        return delegate.listenBlockingStreamingAndAwait(service);
    }
}
