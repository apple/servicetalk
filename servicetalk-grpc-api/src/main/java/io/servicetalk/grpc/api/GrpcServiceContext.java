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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.TrailersTransformer;
import io.servicetalk.transport.api.ConnectionContext;

import java.util.List;
import java.util.function.Supplier;

/**
 * A <a href="https://www.grpc.io">gRPC</a> service context.
 */
public interface GrpcServiceContext extends ConnectionContext, GrpcMetadata {

    @Override
    GrpcExecutionContext executionContext();

    @Override
    GrpcProtocol protocol();

    /**
     * The {@link ContentCodec} codings available for this <a href="https://www.grpc.io">gRPC</a> call.
     * @return the {@link ContentCodec} codings available for this <a href="https://www.grpc.io">gRPC</a> call.
     * @deprecated Will be removed along with {@link ContentCodec}.
     */
    @Deprecated
    List<ContentCodec> supportedMessageCodings();

    /**
     * {@inheritDoc}
     * <p>
     * <b>Notes</b>:
     * <ol>
     *     <li>For asynchronous endpoints that operate with a {@link Publisher} either for reading or writing data back,
     *     only modifications to the {@link #responseContext()} made before the endpoint method returns are visible for
     *     HTTP {@link StreamingHttpResponse#headers() headers}. Any other modifications made from inside the
     *     asynchronous chain of operators or from inside the {@link Publisher#defer(Supplier)} operator will be visible
     *     only for HTTP {@link StreamingHttpResponse#transform(TrailersTransformer) trailers}.</li>
     *     <li>For synchronous endpoints that operate with {@link BlockingStreamingGrpcServerResponse}, only
     *     modifications to the {@link #responseContext()} made before invocation of
     *     {@link BlockingStreamingGrpcServerResponse#sendMetaData()} method are visible for HTTP
     *     {@link StreamingHttpResponse#headers() headers}. Any other modifications made later (while operating with
     *     {@link GrpcPayloadWriter}) will be visible only for HTTP
     *     {@link StreamingHttpResponse#transform(TrailersTransformer) trailers}.</li>
     * </ol>
     */
    @Override
    default ContextMap responseContext() {
        return GrpcMetadata.super.responseContext();
    }

    interface GrpcProtocol extends Protocol {

        HttpProtocolVersion httpProtocol();
    }
}
