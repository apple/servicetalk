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
package io.servicetalk.examples.http.helloworld.async;

import io.servicetalk.http.api.HttpRequestHandler;
import io.servicetalk.http.netty.HttpServers;

import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;

public final class HelloWorldServer {

    public static void main(String[] args) throws Exception {
        HttpServers.newHttpServerBuilder(8080)
                .listenAndAwait((ctx, request, responseFactory) ->
                        success(responseFactory.ok()
                                .payloadBody("Hello World!", textSerializer())))
                .awaitShutdown();
    }
}
