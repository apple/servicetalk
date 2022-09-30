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
package io.servicetalk.examples.http.serialization.bytes.blocking;

import io.servicetalk.http.netty.HttpServers;

import static io.servicetalk.examples.http.serialization.bytes.SerializerUtils.SERIALIZER;
import static io.servicetalk.serializer.utils.ByteArraySerializer.byteArraySerializer;

public final class BlockingPojoServer {
    private static final byte[] BYTES = new byte[] { 'H', 'e', 'l', 'l', 'o', ' ', 'W', 'o', 'r', 'l', 'd' };
    public static void main(String[] args) throws Exception {
        HttpServers.forPort(8080)
                .listenBlockingAndAwait((ctx, request, responseFactory) ->
                        responseFactory.ok().payloadBody(BYTES, SERIALIZER))
                .awaitShutdown();
    }
}
