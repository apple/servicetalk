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
package io.servicetalk.examples.http.jaxrs.client;

import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.tests.helloworld.HelloReply;
import io.servicetalk.tests.helloworld.HelloRequest;

import static io.servicetalk.examples.http.jaxrs.client.SerializerUtils.REQ_SERIALIZER;
import static io.servicetalk.examples.http.jaxrs.client.SerializerUtils.REQ_STREAMING_SERIALIZER;
import static io.servicetalk.examples.http.jaxrs.client.SerializerUtils.RESP_SERIALIZER;
import static io.servicetalk.examples.http.jaxrs.client.SerializerUtils.RESP_STREAMING_SERIALIZER;
import static java.util.Arrays.asList;

public class ProtobufClient {
    public static void main(String[] args) throws Exception {
        System.out.println("==Scalar Requests==");
        try (BlockingHttpClient client = HttpClients.forSingleAddress("localhost", 8080).buildBlocking()) {
            scalarRequest(client, "/greetings/hello");
            scalarRequest(client, "/greetings/single-hello");
        }

        System.out.println("==VarInt Streaming Requests==");
        try (BlockingStreamingHttpClient client = HttpClients.forSingleAddress("localhost", 8080)
                .buildBlockingStreaming()) {
            for (HelloReply reply : client.request(client.post("/greetings/publisher-hello")
                    .payloadBody(asList(
                                    HelloRequest.newBuilder().setName("world1").build(),
                                    HelloRequest.newBuilder().setName("world2").build()),
                            REQ_STREAMING_SERIALIZER))
                    .payloadBody(RESP_STREAMING_SERIALIZER)) {
                System.out.println(reply);
            }
        }
    }

    private static void scalarRequest(BlockingHttpClient client, String path) throws Exception {
        HttpResponse resp = client.request(client.post(path)
                .payloadBody(HelloRequest.newBuilder().setName("world").build(), REQ_SERIALIZER));
        System.out.println(resp.toString((name, value) -> value));
        System.out.println(resp.payloadBody(RESP_SERIALIZER));
    }
}
