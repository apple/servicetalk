/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.netty.HttpServers;

import java.io.OutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class BlockingHelloWorldOutputStreamServer {

    private static final byte[] HELLO = "Hello\n".getBytes(UTF_8);
    private static final byte[] WORLD = "World\n".getBytes(UTF_8);

    public static void main(String[] args) throws Exception {
        HttpServers.forPort(8080).listenBlockingStreamingAndAwait((ctx, request, response) -> {
            try (OutputStream os = response.sendMetaDataOutputStream()) {
                os.write(HELLO);
                os.write(WORLD);
            }
        }).awaitShutdown();
    }
}
