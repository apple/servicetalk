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
package io.servicetalk.examples.http.serialization.async;

import io.servicetalk.examples.http.serialization.CreatePojoRequest;
import io.servicetalk.examples.http.serialization.PojoResponse;
import io.servicetalk.http.netty.HttpServers;

import java.util.concurrent.ThreadLocalRandom;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.examples.http.serialization.SerializerUtils.REQ_SERIALIZER;
import static io.servicetalk.examples.http.serialization.SerializerUtils.RESP_SERIALIZER;
import static io.servicetalk.http.api.HttpHeaderNames.ALLOW;
import static io.servicetalk.http.api.HttpRequestMethod.POST;

public final class PojoServer {
    public static void main(String[] args) throws Exception {
        HttpServers.forPort(8080)
                .listenAndAwait((ctx, request, responseFactory) -> {
                    if (!"/pojos".equals(request.requestTarget())) {
                        return succeeded(responseFactory.notFound());
                    }
                    if (!POST.equals(request.method())) {
                        return succeeded(responseFactory.methodNotAllowed().addHeader(ALLOW, POST.name()));
                    }
                    CreatePojoRequest req = request.payloadBody(REQ_SERIALIZER);
                    return succeeded(responseFactory.created()
                            .payloadBody(new PojoResponse(ThreadLocalRandom.current().nextInt(100), req.getValue()),
                                    RESP_SERIALIZER));
                })
                .awaitShutdown();
    }
}
