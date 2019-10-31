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
package io.servicetalk.examples.http.http2.alpn.async;

import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.test.resources.DefaultTestCerts;

import java.util.concurrent.CountDownLatch;

import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;

/**
 * Client with an asynchronous and aggregated programming paradigm that negotiates
 * <a href="https://tools.ietf.org/html/rfc7540#section-3.3">HTTP/2</a> or
 * <a href="https://tools.ietf.org/html/rfc7231">HTTP/1.1</a> using
 * <a href="https://tools.ietf.org/html/rfc7301">ALPN extension</a> for TLS connections.
 */
public final class AlpnHttpClient {

    public static void main(String[] args) throws Exception {
        try (HttpClient client = HttpClients.forSingleAddress("localhost", 8080)
                .protocols(h2Default(), h1Default()) // Configure support for HTTP/2 and HTTP/1.1 protocols
                .secure()   // Start TLS configuration
                .disableHostnameVerification()  // Our self-signed certificates does not support hostname verification,
                // but this MUST NOT be disabled in production because it may leave you vulnerable to MITM attacks
                .trustManager(DefaultTestCerts::loadMutualAuthCaPem)    // Custom trust manager for test certificates
                .commit()   // Finish TLS configuration
                .build()) {
            // This example is demonstrating asynchronous execution, but needs to prevent the main thread from exiting
            // before the response has been processed. This isn't typical usage for a streaming API but is useful for
            // demonstration purposes.
            CountDownLatch responseProcessedLatch = new CountDownLatch(1);
            client.request(client.get("/"))
                    .whenFinally(responseProcessedLatch::countDown)
                    .subscribe(resp -> {
                        System.out.println(resp.toString((name, value) -> value));
                        System.out.println(resp.payloadBody(textDeserializer()));
                    });

            responseProcessedLatch.await();
        }
    }
}
