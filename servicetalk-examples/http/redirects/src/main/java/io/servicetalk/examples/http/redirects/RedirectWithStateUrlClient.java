/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.http.redirects;

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;

import static io.servicetalk.concurrent.api.AsyncContextMap.Key.newKey;
import static io.servicetalk.examples.http.redirects.RedirectingServer.CUSTOM_HEADER;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.REDIRECTION_3XX;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;

/**
 * Async `Hello World` example that demonstrates how redirects can be handled automatically by a
 * {@link HttpClients#forMultiAddressUrl() multi-address} client. It demonstrates how users can preserve headers and
 * payload body of the original request while redirecting.
 *
 * For security reasons, headers and payload body are not automatically populated for the redirect request. Users have
 * to explicitly cary the original state when they are sure that redirect does not forward to a malicious target server.
 *
 * For backward compatibility with legacy HTTP clients, ServiceTalk preserves behavior for
 * {@link HttpRequestMethod#POST} requests described in RFC7231, sections
 * <a href="https://datatracker.ietf.org/doc/html/rfc7231#section-6.4.2">6.4.2</a> and
 * <a href="https://datatracker.ietf.org/doc/html/rfc7231#section-6.4.3">6.4.3</a>. For these status codes, manual
 * action may be required to preserve the original request method.
 */
public final class RedirectWithStateUrlClient {

    private static final AsyncContextMap.Key<StreamingHttpRequest> ORIGINAL_REQUEST = newKey();

    public static void main(String... args) throws Exception {
        try (HttpClient client = HttpClients.forMultiAddressUrl()
                // This is an optional configuration that applies more restrictive limit for the number or redirects:
                .maxRedirects(1)
                .initializer((scheme, address, builder) -> {
                    // Add a filter to preserve the original request information, while redirecting:
                    builder.appendClientFilter(c -> new StreamingHttpClientFilter(c) {
                        @Override
                        protected Single<StreamingHttpResponse> request(StreamingHttpRequester delegate,
                                                                        HttpExecutionStrategy strategy,
                                                                        StreamingHttpRequest request) {
                            StreamingHttpRequest original = AsyncContext.get(ORIGINAL_REQUEST);
                            if (original != null) {
                                // If the previous response was a redirect, carry headers and payload body. But before
                                // doing so, make sure that the redirect location is trusted to avoid leaking sensitive
                                // content. It can be done by manual checking of the target hostname and port number or
                                // by configuring mutual TLS. This is out of scope of this example.
                                CharSequence headerValue = original.headers().get(CUSTOM_HEADER);
                                if (headerValue != null) {
                                    request.setHeader(CUSTOM_HEADER, headerValue);
                                }
                                // As described in javadoc for this example, 301 and 302 status codes may change
                                // POST to GET for the redirect. Override it explicitly to preserve POST.
                                if (POST.equals(original.method())) {
                                    request.method(POST)
                                            // Note: the original request must have re-playable payload body publisher.
                                            .payloadBody(original.payloadBody());
                                    // Preserve correct header for the payload body length/format definition:
                                    CharSequence cl = original.headers().get(CONTENT_LENGTH);
                                    if (cl != null) {
                                        request.setHeader(CONTENT_LENGTH, cl);
                                    } else {
                                        request.setHeader(TRANSFER_ENCODING, CHUNKED);
                                    }
                                }
                            }
                            return delegate().request(strategy, request).map(response -> {
                                if (response.status().statusClass() == REDIRECTION_3XX) {
                                    // In case of a redirect, remember original request in the context:
                                    AsyncContext.put(ORIGINAL_REQUEST, request);
                                } else {
                                    // Clear the state to make sure retries won't use the state in the context:
                                    AsyncContext.remove(ORIGINAL_REQUEST);
                                }
                                return response;
                            });
                        }
                    });
                    // The custom SSL configuration here is necessary only because this example uses self-signed
                    // certificates. For cases when it's enough to use the local trust store, MultiAddressUrl client
                    // already provides default SSL configuration and this step may be skipped.
                    if ("https".equalsIgnoreCase(scheme)) {
                        builder.sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem).build());
                    }
                })
                .build()) {
            client.request(client.post("http://localhost:8080/sayHello")
                    .addHeader(CUSTOM_HEADER, "value")
                    .payloadBody(client.executionContext().bufferAllocator().fromAscii("some_content")))
                    .whenOnSuccess(resp -> {
                        System.out.println(resp.toString((name, value) -> value));
                        System.out.println(resp.payloadBody(textSerializerUtf8()));
                    })
            // This example is demonstrating asynchronous execution, but needs to prevent the main thread from exiting
            // before the response has been processed. This isn't typical usage for an asynchronous API but is useful
            // for demonstration purposes.
                    .toFuture().get();
        }
    }
}
