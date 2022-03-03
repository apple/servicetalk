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
package io.servicetalk.examples.http.serialization.json.blocking;

import io.servicetalk.examples.http.serialization.json.CreatePojoRequest;
import io.servicetalk.examples.http.serialization.json.PojoResponse;
import io.servicetalk.http.netty.HttpServers;

import java.util.concurrent.ThreadLocalRandom;

import static io.servicetalk.examples.http.serialization.json.SerializerUtils.REQ_SERIALIZER;
import static io.servicetalk.examples.http.serialization.json.SerializerUtils.RESP_SERIALIZER;
import static io.servicetalk.http.api.HttpHeaderNames.ALLOW;
import static io.servicetalk.http.api.HttpRequestMethod.POST;

public final class BlockingPojoServer {
    public static void main(String[] args) throws Exception {
        HttpServers.forPort(8080)
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    if (!"/pojos".equals(request.requestTarget())) {
                        return responseFactory.notFound();
                    }
                    if (!POST.equals(request.method())) {
                        return responseFactory.methodNotAllowed().addHeader(ALLOW, POST.name());
                    }
                    CreatePojoRequest req = request.payloadBody(REQ_SERIALIZER);
                    return responseFactory.created()
                            .payloadBody(new PojoResponse(ThreadLocalRandom.current().nextInt(100), req.getValue()),
                                    RESP_SERIALIZER);
                })
                .awaitShutdown();
    }
}
