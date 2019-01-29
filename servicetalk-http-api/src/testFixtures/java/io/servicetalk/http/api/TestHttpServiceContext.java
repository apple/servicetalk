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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.api.Completable.completed;

public class TestHttpServiceContext extends HttpServiceContext {
    private final ExecutionContext executionContext;
    private final SocketAddress localAddress;
    private final SocketAddress remoteAddress;

    public TestHttpServiceContext(StreamingHttpRequestResponseFactory reqRespFactory,
                                  ExecutionContext executionContext) {
        super(new StreamingHttpRequestResponseFactoryToHttpRequestResponseFactory(reqRespFactory),
                reqRespFactory,
                new StreamingHttpRequestResponseFactoryToBlockingStreamingHttpRequestResponseFactory(reqRespFactory));
        this.executionContext = executionContext;
        remoteAddress = localAddress = new InetSocketAddress(0);
    }

    @Override
    public SocketAddress localAddress() {
        return localAddress;
    }

    @Override
    public SocketAddress remoteAddress() {
        return remoteAddress;
    }

    @Nullable
    @Override
    public SSLSession sslSession() {
        return null;
    }

    @Override
    public ExecutionContext executionContext() {
        return executionContext;
    }

    @Override
    public Single<Throwable> transportError() {
        return Single.never();
    }

    @Override
    public Completable onClose() {
        return completed();
    }

    @Override
    public Completable closeAsync() {
        return completed();
    }
}
