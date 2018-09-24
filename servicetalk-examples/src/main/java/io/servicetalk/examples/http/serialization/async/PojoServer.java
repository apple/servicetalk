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

import io.servicetalk.data.jackson.JacksonSerializationProvider;
import io.servicetalk.examples.http.serialization.MyPojo;
import io.servicetalk.examples.http.serialization.PojoRequest;
import io.servicetalk.http.api.HttpSerializationProvider;
import io.servicetalk.http.netty.DefaultHttpServerStarter;

import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpSerializationProviders.jsonSerializer;

public final class PojoServer {

    public static void main(String[] args) throws Exception {
        HttpSerializationProvider serializer = jsonSerializer(new JacksonSerializationProvider());
        new DefaultHttpServerStarter()
                .start(8080, (ctx, request, responseFactory) -> {
                    PojoRequest req = request.payloadBody(serializer.deserializerFor(PojoRequest.class));
                    return success(responseFactory.ok()
                            .payloadBody(new MyPojo(req.getId(), "foo"), serializer.serializerFor(MyPojo.class)));
                })
                .toFuture().get()
                .awaitShutdown();
    }
}
