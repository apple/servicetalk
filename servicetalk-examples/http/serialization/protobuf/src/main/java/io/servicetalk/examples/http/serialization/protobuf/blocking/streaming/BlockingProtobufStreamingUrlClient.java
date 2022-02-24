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
package io.servicetalk.examples.http.serialization.protobuf.blocking.streaming;

import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.examples.http.serialization.protobuf.ExampleProtos.RequestMessage;
import io.servicetalk.examples.http.serialization.protobuf.ExampleProtos.ResponseMessage;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpResponse;
import io.servicetalk.http.netty.HttpClients;

import static io.servicetalk.examples.http.serialization.protobuf.SerializerUtils.REQ_STREAMING_SERIALIZER;
import static io.servicetalk.examples.http.serialization.protobuf.SerializerUtils.RESP_STREAMING_SERIALIZER;
import static java.util.Arrays.asList;

public final class BlockingProtobufStreamingUrlClient {
    public static void main(String[] args) throws Exception {
        try (BlockingStreamingHttpClient client = HttpClients.forMultiAddressUrl().buildBlockingStreaming()) {
            BlockingStreamingHttpResponse response = client.request(client.post("http://localhost:8080/protobuf")
                    .payloadBody(asList(
                            RequestMessage.newBuilder().setMessage("value1").build(),
                            RequestMessage.newBuilder().setMessage("value22").build(),
                            RequestMessage.newBuilder().setMessage("value333").build()),
                            REQ_STREAMING_SERIALIZER));
            System.out.println(response.toString((name, value) -> value));
            // While it's also possible to use for-each, it's recommended to use try-with-resources to make sure that
            // the full response payload body is drained in case of exceptions
            try (BlockingIterator<ResponseMessage> payload =
                         response.payloadBody(RESP_STREAMING_SERIALIZER).iterator()) {
                while (payload.hasNext()) {
                    System.out.println(payload.next());
                }
            }
        }
    }
}
