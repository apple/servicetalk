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
package io.servicetalk.examples.http.serialization.blocking.streaming;

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.data.jackson.JacksonSerializationProvider;
import io.servicetalk.examples.http.serialization.CreatePojoRequest;
import io.servicetalk.examples.http.serialization.MyPojo;
import io.servicetalk.http.api.HttpSerializationProvider;
import io.servicetalk.http.netty.HttpServers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.http.api.HttpHeaderNames.ALLOW;
import static io.servicetalk.http.api.HttpRequestMethods.POST;
import static io.servicetalk.http.api.HttpSerializationProviders.jsonSerializer;

public final class BlockingPojoStreamingServer {

    public static void main(String[] args) throws Exception {
        HttpSerializationProvider serializer = jsonSerializer(new JacksonSerializationProvider());
        HttpServers.newHttpServerBuilder(8080)
                .listenBlockingStreamingAndAwait((ctx, request, responseFactory) -> {
                    if (request.method() != POST) {
                        return responseFactory.methodNotAllowed().addHeader(ALLOW, POST.name());
                    }
                    BlockingIterable<CreatePojoRequest> values = request
                            .payloadBody(serializer.deserializerFor(CreatePojoRequest.class));
                    List<MyPojo> pojos = new ArrayList<>();
                    AtomicInteger newId = new AtomicInteger(ThreadLocalRandom.current().nextInt(100));
                    try (BlockingIterator<CreatePojoRequest> iterator = values.iterator()) {
                        while (iterator.hasNext()) {
                            CreatePojoRequest req = iterator.next();
                            if (req != null) {
                                pojos.add(new MyPojo(newId.getAndIncrement(), req.getValue()));
                            }
                        }
                    }
                    return responseFactory.created()
                            .payloadBody(pojos, serializer.serializerFor(MyPojo.class));
                })
                .awaitShutdown();
    }
}
