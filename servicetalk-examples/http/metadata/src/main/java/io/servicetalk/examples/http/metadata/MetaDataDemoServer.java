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
package io.servicetalk.examples.http.metadata;

import io.servicetalk.http.netty.HttpServers;

import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LANGUAGE;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;

/**
 * Demonstration server to be used with {@link MetaDataDemoClient}.
 * <p>
 * Serves up "hello world" in multiple languages.
 * "en" and "fr" return greetings.
 * "broken" returns a "greeting" in an invalid language.
 * Anything else returns a 400 Bad Request response.
 * <p>
 * Demonstrates a few features:
 * <ul>
 * <li>Reading query parameters.</li>
 * <li>Setting response status.</li>
 * <li>Setting response headers.</li>
 * </ul>
 */
public final class MetaDataDemoServer {

    public static void main(String[] args) throws Exception {
        HttpServers.forPort(8080)
                // Note: this example demonstrates only blocking-aggregated programming paradigm, for asynchronous and
                // streaming API see helloworld examples.
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    String languageCode = request.queryParameter("language");
                    final String helloText;
                    if ("en".equals(languageCode)) {
                        helloText = "Hello World!";
                    } else if ("fr".equals(languageCode)) {
                        helloText = "Bonjour monde!";
                    } else {
                        return responseFactory.badRequest();
                    }
                    return responseFactory.ok()
                            // Return the language in upper case, to demonstrate the case-insensitive compare
                            // in the client.
                            .addHeader(CONTENT_LANGUAGE, languageCode.toUpperCase())
                            .payloadBody(helloText, textSerializerUtf8());
                })
                .awaitShutdown();
    }
}
