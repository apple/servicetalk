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
package io.servicetalk.examples.http.opentracing;

import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.opentracing.http.TracingHttpRequesterFilter;
import io.servicetalk.opentracing.inmemory.DefaultInMemoryTracer;
import io.servicetalk.opentracing.zipkin.publisher.ZipkinPublisher;
import io.servicetalk.opentracing.zipkin.publisher.reporter.LoggingReporter;

import io.opentracing.Tracer;

import static io.servicetalk.opentracing.asynccontext.AsyncContextInMemoryScopeManager.SCOPE_MANAGER;
import static io.servicetalk.opentracing.zipkin.publisher.reporter.LoggingReporter.*;

/**
 * A client that does distributed tracing.
 */
public final class OpenTracingClient {
    public static void main(String[] args) throws Exception {
        // Publishing to Zipkin is optional, but demonstrated for completeness.
        try (ZipkinPublisher zipkinPublisher = new ZipkinPublisher.Builder("servicetalk-test-client", CONSOLE).build();
             Tracer tracer = new DefaultInMemoryTracer.Builder(SCOPE_MANAGER).addListener(zipkinPublisher).build();
             BlockingHttpClient client = HttpClients.forSingleAddress("localhost", 8080)
                     .appendClientFilter(new TracingHttpRequesterFilter(tracer, "servicetalk-test-client"))
                     .buildBlocking()) {
            HttpResponse response = client.request(client.get("/"));
            System.out.println(response.toString((name, value) -> value));
        }
    }
}
