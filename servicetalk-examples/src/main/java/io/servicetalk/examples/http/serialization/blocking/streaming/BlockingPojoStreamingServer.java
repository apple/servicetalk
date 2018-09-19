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
import io.servicetalk.examples.http.serialization.MyPojo;
import io.servicetalk.examples.http.serialization.PojoRequest;
import io.servicetalk.http.api.HttpSerializationProvider;
import io.servicetalk.http.netty.DefaultHttpServerStarter;

import java.util.ArrayList;
import java.util.List;

import static io.servicetalk.http.api.HttpSerializationProviders.serializeJson;

public class BlockingPojoStreamingServer {

    public static void main(String[] args) throws Exception {
        HttpSerializationProvider serializer = serializeJson(new JacksonSerializationProvider());
        new DefaultHttpServerStarter()
                .startBlockingStreaming(8080, (ctx, request, responseFactory) -> {
                    BlockingIterable<PojoRequest> ids = request.getPayloadBody(serializer.deserializerFor(PojoRequest.class));
                    List<MyPojo> pojos = new ArrayList<>();
                    try (BlockingIterator<PojoRequest> iterator = ids.iterator()) {
                        while (iterator.hasNext()) {
                            PojoRequest req = iterator.next();
                            if (req != null) {
                                pojos.add(new MyPojo(req.getId(), "foo"));
                            }
                        }
                    }
                    return responseFactory.ok()
                            .setPayloadBody(pojos, serializer.serializerFor(MyPojo.class));
                })
                .awaitShutdown();
    }
}
