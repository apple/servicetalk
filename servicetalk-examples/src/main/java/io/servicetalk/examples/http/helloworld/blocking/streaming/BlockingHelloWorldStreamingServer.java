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

import io.servicetalk.http.netty.DefaultHttpServerStarter;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.HttpSerializationProviders.serializerForUtf8PlainText;

public final class BlockingHelloWorldStreamingServer {
    public static void main(String[] args) throws Exception {
        new DefaultHttpServerStarter()
                .startBlockingStreaming(8080, (ctx, request, responseFactory) ->
                        responseFactory.ok()
                                //TODO: This would use setPayloadBody(Iterable, HttpSerializer) when available.
                                .transformPayloadBody(from("Hello\n", " World\n", " From\n", " ServiceTalk\n"),
                                        serializerForUtf8PlainText()))
                .toFuture().get()
                .awaitShutdown();
    }
}
