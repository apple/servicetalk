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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.data.jackson.JacksonSerializationProvider;
import io.servicetalk.examples.http.serialization.MyPojo;
import io.servicetalk.examples.http.serialization.PojoRequest;
import io.servicetalk.http.api.HttpSerializationProvider;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.netty.HttpClients;

import static io.servicetalk.http.api.HttpSerializationProviders.serializeJson;

public class PojoStreamingUrlClient {

    public static void main(String[] args) {
        HttpSerializationProvider serializer = serializeJson(new JacksonSerializationProvider());
        try (StreamingHttpClient client = HttpClients.forMultiAddressUrl().buildStreaming()) {
            client.request(client.get("http://localhost:8080/getPojo")
                    .transformPayloadBody(Publisher.from("1", "2", "3").map(PojoRequest::new),
                            serializer.serializerFor(PojoRequest.class)))
                    .doBeforeSuccess(System.out::println)
                    .flatMapPublisher(resp -> resp.getPayloadBody(serializer.deserializerFor(MyPojo.class)))
                    .forEach(System.out::println);
        }
    }
}
