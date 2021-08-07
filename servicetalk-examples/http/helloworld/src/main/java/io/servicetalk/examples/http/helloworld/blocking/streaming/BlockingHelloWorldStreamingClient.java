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
package io.servicetalk.examples.http.helloworld.blocking.streaming;

import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpResponse;
import io.servicetalk.http.netty.HttpClients;

import static io.servicetalk.http.api.HttpSerializers.appSerializerUtf8FixLen;

public final class BlockingHelloWorldStreamingClient {

    public static void main(String[] args) throws Exception {
        try (BlockingStreamingHttpClient client = HttpClients.forSingleAddress("localhost", 8080)
                .buildBlockingStreaming()) {
            BlockingStreamingHttpResponse response = client.request(client.get("/sayHello"));
            System.out.println(response.toString((name, value) -> value));
            try (BlockingIterator<String> payload = response.payloadBody(appSerializerUtf8FixLen()).iterator()) {
                while (payload.hasNext()) {
                    System.out.println(payload.next());
                }
            }
        }
    }
}
