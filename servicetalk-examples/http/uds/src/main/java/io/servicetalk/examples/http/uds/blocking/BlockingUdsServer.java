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

import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.transport.api.DomainSocketAddress;

import java.io.File;

import static io.servicetalk.examples.http.uds.blocking.UdsUtils.udsAddress;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;

/**
 * <a href="http://man7.org/linux/man-pages/man7/unix.7.html">AF_UNIX socket</a> server example.
 */
public final class BlockingUdsServer {
    public static void main(String[] args) throws Exception {
        DomainSocketAddress udsAddress = udsAddress();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // After the server is done, clean up the file. This doesn't cover all cases (external forced shutdown,
            // JVM crash, etc.) but best-effort cleanup is sufficient for temp file to allow the example to re-run.
            if (!new File(udsAddress.getPath()).delete()) {
                System.err.println("failed to delete UDS file: " + udsAddress);
            }
        }));

        HttpServers.forAddress(udsAddress)
                .listenBlockingAndAwait((ctx, request, responseFactory) ->
                        responseFactory.ok().payloadBody("Hello World!", textSerializerUtf8()))
                .awaitShutdown();
    }
}
