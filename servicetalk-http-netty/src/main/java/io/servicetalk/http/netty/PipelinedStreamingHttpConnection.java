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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.netty.internal.Connection;
import io.servicetalk.transport.netty.internal.DefaultPipelinedConnection;

import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;

final class PipelinedStreamingHttpConnection extends AbstractStreamingHttpConnection<DefaultPipelinedConnection<Object, Object>> {

    PipelinedStreamingHttpConnection(final Connection<Object, Object> connection,
                                     final ReadOnlyHttpClientConfig config,
                                     final ExecutionContext executionContext,
                                     final StreamingHttpRequestResponseFactory reqRespFactor) {
        super(new DefaultPipelinedConnection<>(connection, config.getMaxPipelinedRequests()),
                connection.onClosing(), config, executionContext, reqRespFactor);
    }

    @Override
    public Single<StreamingHttpResponse> request(StreamingHttpRequest request) {
        HttpProtocolVersion version = request.version();
        if (version != HTTP_1_1 && (version.majorVersion() != HTTP_1_1.majorVersion()
                || version.minorVersion() != HTTP_1_1.minorVersion())) {
            return Single.error(new IllegalArgumentException(
                    "Pipelining unsupported in protocol version: " + request.version()));
        }
        return super.request(request);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Publisher<Object> writeAndRead(Publisher<Object> requestStream) {
        return connection.request(requestStream);
    }
}
