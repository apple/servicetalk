/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.opentelemetry.http;

import io.servicetalk.http.api.HttpClient;
import io.servicetalk.transport.api.ServerContext;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.opentelemetry.http.TestUtils.clientBuilder;
import static io.servicetalk.opentelemetry.http.TestUtils.httpServerBuilder;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies when {@link OpenTelemetry} is resolved by the filter builders. {@link GlobalOpenTelemetry} is process-wide
 * state, so it is reset around every test to observe whether and when a filter builder accesses it.
 */
class OpenTelemetryFilterBuilderTest {

    private enum FilterKind {
        SERVICE {
            @Override
            OpenTelemetryFilterBuilder<?> newBuilder() {
                return new OpenTelemetryHttpServiceFilter.Builder();
            }

            @Override
            void build(final OpenTelemetryFilterBuilder<?> builder) {
                ((OpenTelemetryHttpServiceFilter.Builder) builder).build();
            }
        },
        REQUESTER {
            @Override
            OpenTelemetryFilterBuilder<?> newBuilder() {
                return new OpenTelemetryHttpRequesterFilter.Builder();
            }

            @Override
            void build(final OpenTelemetryFilterBuilder<?> builder) {
                ((OpenTelemetryHttpRequesterFilter.Builder) builder).build();
            }
        };

        abstract OpenTelemetryFilterBuilder<?> newBuilder();

        abstract void build(OpenTelemetryFilterBuilder<?> builder);
    }

    @BeforeEach
    void resetGlobal() {
        GlobalOpenTelemetry.resetForTest();
    }

    @AfterEach
    void restoreGlobal() {
        GlobalOpenTelemetry.resetForTest();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(FilterKind.class)
    void buildWithOpenTelemetryDoesNotAccessGlobal(FilterKind kind) {
        OpenTelemetryFilterBuilder<?> builder = kind.newBuilder();
        builder.openTelemetry(OpenTelemetry.noop());
        kind.build(builder);
        assertThat(GlobalOpenTelemetry.isSet(), is(false));
    }

    @Test
    void openTelemetryNullIsRejected() {
        OpenTelemetryHttpServiceFilter.Builder builder = new OpenTelemetryHttpServiceFilter.Builder();
        assertThrows(NullPointerException.class, () -> builder.openTelemetry(null));
    }

    @Test
    void explicitOpenTelemetryReceivesSpansInsteadOfGlobal() throws Exception {
        try (SpanCollector server = new SpanCollector();
             SpanCollector client = new SpanCollector();
             SpanCollector global = new SpanCollector()) {
            GlobalOpenTelemetry.set(global.openTelemetry);
            sendRequest(new OpenTelemetryHttpServiceFilter.Builder().openTelemetry(server.openTelemetry),
                    new OpenTelemetryHttpRequesterFilter.Builder().openTelemetry(client.openTelemetry),
                    server, client);

            assertThat(server.kinds(), contains(SpanKind.SERVER));
            assertThat(client.kinds(), contains(SpanKind.CLIENT));
            assertThat(global.spans(), is(empty()));
        }
    }

    @Test
    void globalRegisteredAfterBuilderCreatedReceivesSpans() throws Exception {
        try (SpanCollector global = new SpanCollector()) {
            OpenTelemetryHttpServiceFilter.Builder serviceBuilder = new OpenTelemetryHttpServiceFilter.Builder();
            OpenTelemetryHttpRequesterFilter.Builder requesterBuilder =
                    new OpenTelemetryHttpRequesterFilter.Builder();
            // The application registers its SDK after a library has already created the builders.
            GlobalOpenTelemetry.set(global.openTelemetry);
            sendRequest(serviceBuilder, requesterBuilder, global, global);

            assertThat(global.kinds(), containsInAnyOrder(SpanKind.SERVER, SpanKind.CLIENT));
        }
    }

    private static void sendRequest(OpenTelemetryHttpServiceFilter.Builder serviceBuilder,
                                    OpenTelemetryHttpRequesterFilter.Builder requesterBuilder,
                                    SpanCollector serverSpans, SpanCollector clientSpans) throws Exception {
        try (ServerContext serverContext = httpServerBuilder(false)
                .appendNonOffloadingServiceFilter(serviceBuilder.build())
                .listenAndAwait((ctx, request, responseFactory) ->
                        succeeded(responseFactory.ok()));
             HttpClient client = clientBuilder(false, serverContext)
                     .appendClientFilter(requesterBuilder.componentName("test").build())
                     .build()) {
            client.request(client.get("/")).toFuture().get();
            // The server span ends after the response was delivered, so wait for the exported spans.
            serverSpans.awaitSpan(SpanKind.SERVER);
            clientSpans.awaitSpan(SpanKind.CLIENT);
        }
    }

    /**
     * An {@link OpenTelemetry} SDK that records finished spans and lets tests wait for them.
     */
    private static final class SpanCollector implements SpanExporter {
        private final List<SpanData> spans = new CopyOnWriteArrayList<>();
        private final CountDownLatch serverSpan = new CountDownLatch(1);
        private final CountDownLatch clientSpan = new CountDownLatch(1);
        final SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(this))
                .build();
        final OpenTelemetry openTelemetry = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();

        @Override
        public CompletableResultCode export(final Collection<SpanData> finished) {
            for (SpanData span : finished) {
                spans.add(span);
                if (span.getKind() == SpanKind.SERVER) {
                    serverSpan.countDown();
                } else if (span.getKind() == SpanKind.CLIENT) {
                    clientSpan.countDown();
                }
            }
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }

        void awaitSpan(SpanKind kind) throws InterruptedException {
            CountDownLatch latch = kind == SpanKind.SERVER ? serverSpan : clientSpan;
            assertTrue(latch.await(DEFAULT_TIMEOUT_SECONDS, SECONDS), "Timed out waiting for a " + kind + " span");
        }

        List<SpanData> spans() {
            return spans;
        }

        List<SpanKind> kinds() {
            return spans.stream().map(SpanData::getKind).collect(Collectors.toList());
        }

        @Override
        public void close() {
            tracerProvider.close();
        }
    }
}
