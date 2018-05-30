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
package io.servicetalk.examples.http.service.composition;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.transport.api.ExecutionContext;

import java.time.Duration;

import static io.servicetalk.examples.http.service.composition.AsyncUtil.timeout;

/**
 * A simple client filter to add a business level timeout for a client. Delegation is a typical way to add
 * functionality to a client.
 */
final class ClientTimeoutFilter extends HttpClient {

    private final Duration timeout;
    private final HttpClient delegate;

    ClientTimeoutFilter(final HttpClient delegate, Duration timeout) {
        this.delegate = delegate;
        this.timeout = timeout;
    }

    @Override
    public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
        return timeout(delegate.request(request), delegate.getExecutionContext().getExecutor(), timeout);
    }

    // ServiceTalk does not currently provide a simple DelegatingHttpClient which will remove this boiler plate code
    // that is just delegating to the original HttpClient
    @Override
    public ExecutionContext getExecutionContext() {
        return delegate.getExecutionContext();
    }

    @Override
    public Single<? extends ReservedHttpConnection> reserveConnection(final HttpRequest<HttpPayloadChunk> request) {
        return delegate.reserveConnection(request);
    }

    @Override
    public Single<? extends UpgradableHttpResponse<HttpPayloadChunk>> upgradeConnection(final HttpRequest<HttpPayloadChunk> request) {
        return delegate.upgradeConnection(request);
    }

    @Override
    public Completable onClose() {
        return delegate.onClose();
    }

    @Override
    public Completable closeAsync() {
        return delegate.closeAsync();
    }
}
