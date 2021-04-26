/*
 * Copyright © 2018-2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.opentracing.zipkin.publisher;

import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.opentracing.inmemory.DefaultInMemoryTracer;
import io.servicetalk.opentracing.inmemory.api.InMemorySpan;
import io.servicetalk.opentracing.inmemory.api.InMemoryTracer;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

import java.io.Closeable;
import java.io.Flushable;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

import static io.opentracing.tag.Tags.SPAN_KIND;
import static io.opentracing.tag.Tags.SPAN_KIND_CLIENT;
import static io.opentracing.tag.Tags.SPAN_KIND_CONSUMER;
import static io.opentracing.tag.Tags.SPAN_KIND_PRODUCER;
import static io.opentracing.tag.Tags.SPAN_KIND_SERVER;
import static io.servicetalk.opentracing.asynccontext.AsyncContextInMemoryScopeManager.SCOPE_MANAGER;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ZipkinPublisherTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private InMemoryTracer tracer;

    @Before
    public void setUp() {
        tracer = new DefaultInMemoryTracer.Builder(SCOPE_MANAGER).persistLogs(true).build();
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testReportSpan() throws ExecutionException, InterruptedException {
        SpanHolder reporter = new SpanHolder();
        try (ZipkinPublisher publisher = buildPublisher(reporter)) {
            InMemorySpan span = buildSpan(SPAN_KIND_SERVER);
            publisher.onSpanFinished(span, SECONDS.toMicros(1));
            publisher.closeAsyncGracefully().toFuture().get();
        }
        assertNotNull(reporter.span);
        assertEquals("test operation", reporter.span.name());
        assertEquals(1000 * 1000, (long) reporter.span.duration());
        Map<String, String> tags = reporter.span.tags();
        assertEquals("string", tags.get("stringKey"));
        assertEquals(Boolean.TRUE.toString(), tags.get("boolKey"));
        assertEquals(String.valueOf(Short.MAX_VALUE), tags.get("shortKey"));
        assertEquals(String.valueOf(Integer.MAX_VALUE), tags.get("intKey"));
        assertEquals(String.valueOf(Long.MAX_VALUE), tags.get("longKey"));
        assertEquals(String.valueOf(Float.MAX_VALUE), tags.get("floatKey"));
        assertEquals(String.valueOf(Double.MAX_VALUE), tags.get("doubleKey"));
        assertTrue(reporter.span.annotations().stream().anyMatch(a -> a.value().equals("some event happened")));
        assertTrue(reporter.flushed);
        assertTrue(reporter.closed);
    }

    @Test
    public void testReportSpanSupportsAllKinds() throws ExecutionException, InterruptedException {
        final HashMap<String, Span.Kind> kinds = new HashMap<>();
        kinds.put(SPAN_KIND_CLIENT, Span.Kind.CLIENT);
        kinds.put(SPAN_KIND_SERVER, Span.Kind.SERVER);
        kinds.put(SPAN_KIND_CONSUMER, Span.Kind.CONSUMER);
        kinds.put(SPAN_KIND_PRODUCER, Span.Kind.PRODUCER);

        SpanHolder reporter = new SpanHolder();
        try (ZipkinPublisher publisher = buildPublisher(reporter)) {
            for (final Map.Entry<String, Span.Kind> kind : kinds.entrySet()) {
                InMemorySpan span = buildSpan(kind.getKey());
                publisher.onSpanFinished(span, SECONDS.toMicros(1));
                publisher.closeAsyncGracefully().toFuture().get();

                assertNotNull(reporter.span);
                assertEquals(kind.getValue(), reporter.span.kind());
                assertTrue(reporter.flushed);
                assertTrue(reporter.closed);
            }
        }
    }

    private ZipkinPublisher buildPublisher(Reporter<Span> reporter) {
        return new ZipkinPublisher.Builder("test", reporter)
                .localAddress(new InetSocketAddress("localhost", 1))
                .build();
    }

    private InMemorySpan buildSpan(final String spanKind) {
        InMemorySpan span = tracer.buildSpan("test operation")
                .withTag(SPAN_KIND.getKey(), spanKind)
                .withTag("stringKey", "string")
                .withTag("boolKey", true)
                .withTag("shortKey", Short.MAX_VALUE)
                .withTag("intKey", Integer.MAX_VALUE)
                .withTag("longKey", Long.MAX_VALUE)
                .withTag("floatKey", Float.MAX_VALUE)
                .withTag("doubleKey", Double.MAX_VALUE)
                .start();
        span.log("some event happened");
        span.finish();
        return span;
    }

    static final class SpanHolder implements Reporter<Span>, Flushable, Closeable {
        @Nullable
        Span span;
        volatile boolean flushed;
        volatile boolean closed;

        @Override
        public void report(final Span span) {
            this.span = span;
        }

        @Override
        public void flush() {
            flushed = true;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
