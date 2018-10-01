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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.concurrent.api.Single.error;

public class TestStreamingHttpClient extends StreamingHttpClient {
    private final ExecutionContext executionContext;

    public TestStreamingHttpClient(StreamingHttpRequestResponseFactory reqRespFactory,
                                   ExecutionContext executionContext) {
        super(reqRespFactory);
        this.executionContext = executionContext;
    }

    @Override
    public Completable closeAsync() {
        return Completable.completed();
    }

    @Override
    public Completable onClose() {
        return Completable.completed();
    }

    @Override
    public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                 final StreamingHttpRequest request) {
        return error(new UnsupportedOperationException());
    }

    @Override
    public ExecutionContext executionContext() {
        return executionContext;
    }

    @Override
    public Single<? extends ReservedStreamingHttpConnection> reserveConnection(final HttpExecutionStrategy strategy,
                                                                               final StreamingHttpRequest request) {
        return error(new UnsupportedOperationException());
    }

    @Override
    public Single<? extends UpgradableStreamingHttpResponse> upgradeConnection(final StreamingHttpRequest request) {
        return error(new UnsupportedOperationException());
    }
}
