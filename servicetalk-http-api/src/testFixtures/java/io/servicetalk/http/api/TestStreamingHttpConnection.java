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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;

public abstract class TestStreamingHttpConnection extends StreamingHttpConnection {
    private final ExecutionContext executionContext;
    private final ConnectionContext connectionContext;

    public TestStreamingHttpConnection(StreamingHttpRequestResponseFactory reqRespFactory,
                                       ExecutionContext executionContext,
                                       ConnectionContext connectionContext) {
        super(reqRespFactory, defaultStrategy());
        this.executionContext = executionContext;
        this.connectionContext = connectionContext;
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
    public final Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        return error(new UnsupportedOperationException());
    }

    @Override
    public ExecutionContext executionContext() {
        return executionContext;
    }

    @Override
    public ConnectionContext connectionContext() {
        return connectionContext;
    }

    @Override
    public <T> Publisher<T> settingStream(final SettingKey<T> settingKey) {
        return Publisher.error(new UnsupportedOperationException());
    }
}
