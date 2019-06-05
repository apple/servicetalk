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
package io.servicetalk.examples.http.serialization.async.streaming;

import io.servicetalk.data.jackson.JacksonSerializationProvider;
import io.servicetalk.examples.http.serialization.CreatePojoRequest;
import io.servicetalk.examples.http.serialization.PojoResponse;
import io.servicetalk.http.api.HttpSerializationProvider;
import io.servicetalk.http.netty.HttpServers;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpHeaderNames.ALLOW;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.api.HttpSerializationProviders.jsonSerializer;

public final class PojoStreamingServer {

    public static void main(String[] args) throws Exception {
        HttpSerializationProvider serializer = jsonSerializer(new JacksonSerializationProvider());
        HttpServers.forPort(8080)
                .listenStreamingAndAwait((ctx, request, responseFactory) -> {
                    if (!"/pojos".equals(request.requestTarget())) {
                        return succeeded(responseFactory.notFound());
                    }
                    if (!POST.equals(request.method())) {
                        return succeeded(responseFactory.methodNotAllowed().addHeader(ALLOW, POST.name()));
                    }
                    AtomicInteger newId = new AtomicInteger(ThreadLocalRandom.current().nextInt(100));
                    return succeeded(responseFactory.created()
                            .payloadBody(request.payloadBody(serializer.deserializerFor(CreatePojoRequest.class))
                                    .map(req -> new PojoResponse(newId.getAndIncrement(), req.getValue())),
                                    serializer.serializerFor(PojoResponse.class)));
                })
                .awaitShutdown();
    }
}
