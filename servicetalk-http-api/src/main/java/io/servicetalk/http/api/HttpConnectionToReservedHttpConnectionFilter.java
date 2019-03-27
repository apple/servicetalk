/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.concurrent.api.Completable.failed;

/**
 * This adapts a {@link StreamingHttpConnectionFilter} to be used as delegate and propagates all calls.
 */
final class HttpConnectionToReservedHttpConnectionFilter extends ReservedStreamingHttpConnectionFilter {

    private final StreamingHttpConnectionFilter connFilter;

    HttpConnectionToReservedHttpConnectionFilter(final StreamingHttpConnectionFilter connFilter) {
        super(terminal(connFilter.reqRespFactory));
        this.connFilter = connFilter;
    }

    @Override
    public ConnectionContext connectionContext() {
        return connFilter.connectionContext();
    }

    @Override
    public <T> Publisher<T> settingStream(final StreamingHttpConnection.SettingKey<T> settingKey) {
        return connFilter.settingStream(settingKey);
    }

    @Override
    protected Single<StreamingHttpResponse> request(final StreamingHttpRequester terminalDelegate,
                                                    final HttpExecutionStrategy strategy,
                                                    final StreamingHttpRequest request) {
        // Don't call the terminal delegate!
        return connFilter.request(strategy, request);
    }

    @Override
    protected HttpExecutionStrategy mergeForEffectiveStrategy(final HttpExecutionStrategy mergeWith) {
        // Because we cross API boundary we need to leverage mergeForEffectiveStrategy() to propagate the final
        // ReservedStreamingHttpConnection.effectiveExecutionStrategy() call on this instance with the real delegate
        // StreamingHttpConnectionFilter.effectiveExecutionStrategy() to cover the entire execution change.
        return connFilter.effectiveExecutionStrategy(mergeWith);
    }

    @Override
    public ExecutionContext executionContext() {
        return connFilter.executionContext();
    }

    @Override
    public Completable onClose() {
        return connFilter.onClose();
    }

    @Override
    public Completable closeAsync() {
        return connFilter.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return connFilter.closeAsyncGracefully();
    }

    @Override
    public String toString() {
        return getClass().getName() + '(' + connFilter + ')';
    }

    @Override
    public Completable releaseAsync() {
        return failed(new UnsupportedOperationException());
    }
}
