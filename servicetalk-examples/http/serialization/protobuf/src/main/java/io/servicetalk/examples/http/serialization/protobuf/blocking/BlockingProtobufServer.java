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
package io.servicetalk.examples.http.serialization.protobuf.blocking;

import io.servicetalk.examples.http.serialization.protobuf.ExampleProtos.RequestMessage;
import io.servicetalk.examples.http.serialization.protobuf.ExampleProtos.ResponseMessage;
import io.servicetalk.http.netty.HttpServers;

import static io.servicetalk.examples.http.serialization.protobuf.SerializerUtils.REQ_SERIALIZER;
import static io.servicetalk.examples.http.serialization.protobuf.SerializerUtils.RESP_SERIALIZER;
import static io.servicetalk.http.api.HttpHeaderNames.ALLOW;
import static io.servicetalk.http.api.HttpRequestMethod.POST;

public final class BlockingProtobufServer {
    public static void main(String[] args) throws Exception {
        HttpServers.forPort(8080)
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    if (!"/protobuf".equals(request.requestTarget())) {
                        return responseFactory.notFound();
                    }
                    if (!POST.equals(request.method())) {
                        return responseFactory.methodNotAllowed().addHeader(ALLOW, POST.name());
                    }

                    RequestMessage req = request.payloadBody(REQ_SERIALIZER);
                    ResponseMessage resp = ResponseMessage.newBuilder().setLength(req.getMessage().length()).build();
                    return responseFactory.created()
                            .payloadBody(resp, RESP_SERIALIZER);
                })
                .awaitShutdown();
    }
}
