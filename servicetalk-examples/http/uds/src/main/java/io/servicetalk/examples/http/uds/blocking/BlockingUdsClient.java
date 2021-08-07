/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.http.uds.blocking;

import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.netty.HttpClients;

import static io.servicetalk.examples.http.uds.blocking.UdsUtils.udsAddress;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;

/**
 * <a href="http://man7.org/linux/man-pages/man7/unix.7.html">AF_UNIX socket</a> client example.
 */
public final class BlockingUdsClient {
    public static void main(String[] args) throws Exception {
        try (BlockingHttpClient client = HttpClients.forResolvedAddress(udsAddress()).buildBlocking()) {
            HttpResponse response = client.request(client.get("/sayHello"));
            System.out.println(response.toString((name, value) -> value));
            System.out.println(response.payloadBody(textSerializerUtf8()));
        }
    }
}
