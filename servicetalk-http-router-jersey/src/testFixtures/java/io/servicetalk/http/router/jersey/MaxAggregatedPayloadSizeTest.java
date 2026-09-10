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
package io.servicetalk.http.router.jersey;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpServerBuilder;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MultivaluedMap;

import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.PAYLOAD_TOO_LARGE;
import static java.util.Collections.singleton;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.hamcrest.Matchers.is;

/**
 * Verifies the server {@code maxAggregatedPayloadSize} limit is honored by aggregating JAX-RS readers and that
 * streaming {@code Publisher<Buffer>} readers are exempt.
 */
class MaxAggregatedPayloadSizeTest extends AbstractJerseyStreamingHttpServiceTest {

    private static final int MAX_PAYLOAD = 16;

    // Applied to the server via configureBuilders(); overridden by tests that exercise warn-only / disabled limits.
    private int maxAggregatedPayloadSize = MAX_PAYLOAD;

    @Path("/echo")
    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @SuppressWarnings("PMD.PublicMemberInNonPublicType") // JAX-RS resource must be public
    public static class EchoResource {
        // Single<Buffer> in/out exercises BufferSingleMessageBodyReaderWriter.
        @POST
        public Single<Buffer> echo(final Single<Buffer> body) {
            return body;
        }

        // Publisher<Buffer> is a streaming reader that must not be bounded by the aggregation limit.
        @POST
        @Path("/stream")
        public Publisher<Buffer> echoStream(final Publisher<Buffer> body) {
            return body;
        }

        // String aggregates via Jersey's built-in reader; PayloadSizeLimitingReaderInterceptor bounds it.
        @POST
        @Path("/string")
        public String echoString(final String body) {
            return body;
        }

        // A raw InputStream is a streaming read; it is exempt from the limit (the app controls consumption).
        @POST
        @Path("/inputstream")
        public String readInputStream(final InputStream in) throws IOException {
            int total = 0;
            final byte[] chunk = new byte[1024];
            for (int read; (read = in.read(chunk)) >= 0;) {
                total += read;
            }
            return Integer.toString(total);
        }

        // A form body aggregates via Jersey's built-in MultivaluedMap reader; the interceptor bounds it.
        @POST
        @Path("/form")
        @Consumes(APPLICATION_FORM_URLENCODED)
        public String echoForm(final MultivaluedMap<String, String> form) {
            return form.getFirst("k");
        }
    }

    static class EchoApplication extends Application {
        @Override
        public Set<Object> getSingletons() {
            return singleton(new EchoResource());
        }
    }

    @Override
    protected Application application() {
        return new EchoApplication();
    }

    @Override
    protected void configureBuilders(final HttpServerBuilder serverBuilder,
                           final HttpJerseyRouterBuilder jerseyRouterBuilder) {
        super.configureBuilders(serverBuilder, jerseyRouterBuilder);
        serverBuilder.maxAggregatedPayloadSize(maxAggregatedPayloadSize);
    }

    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(RouterApi.class)
    void withinLimitSucceeds(final RouterApi api) throws Exception {
        setUp(api);
        final String body = repeat('x', MAX_PAYLOAD);
        sendAndAssertResponse(post("/echo", body, TEXT_PLAIN), OK, TEXT_PLAIN, body);
    }

    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(RouterApi.class)
    void overLimitRejected(final RouterApi api) throws Exception {
        setUp(api);
        assertOverLimitRejected("/echo");
    }

    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(RouterApi.class)
    void overLimitStringReaderRejected(final RouterApi api) throws Exception {
        // The built-in String reader aggregates the whole entity; the interceptor bounds it.
        setUp(api);
        assertOverLimitRejected("/echo/string");
    }

    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(RouterApi.class)
    void overLimitFormReaderRejected(final RouterApi api) throws Exception {
        // The built-in form reader aggregates the whole entity; the interceptor bounds it.
        setUp(api);
        assertOverLimitRejected("/echo/form", "k=" + repeat('x', MAX_PAYLOAD), APPLICATION_FORM_URLENCODED);
    }

    private void assertOverLimitRejected(final String path) {
        assertOverLimitRejected(path, repeat('x', MAX_PAYLOAD + 1), TEXT_PLAIN);
    }

    private void assertOverLimitRejected(final String path, final String body, final CharSequence contentType) {
        try {
            sendAndAssertStatusOnly(post(path, body, contentType), PAYLOAD_TOO_LARGE);
        } catch (RuntimeException e) {
            // Rejecting an oversized body cancels the in-flight upload, so a connection teardown may race ahead of the
            // mapped 413; both mean the payload was rejected. A timeout instead indicates a hang and must fail.
            if (e.getCause() instanceof TimeoutException) {
                throw e;
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(value = RouterApi.class, names = {"ASYNC_STREAMING", "BLOCKING_STREAMING"})
    void rawInputStreamReaderNotLimited(final RouterApi api) throws Exception {
        // A raw InputStream reader streams the entity, so it is exempt; only the streaming paradigms reach it without
        // aggregating (and rejecting) the whole request at the server boundary first.
        setUp(api);
        final int size = MAX_PAYLOAD * 4;
        sendAndAssertResponse(post("/echo/inputstream", repeat('x', size), TEXT_PLAIN),
                OK, TEXT_PLAIN, Integer.toString(size));
    }

    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(value = RouterApi.class, names = {"ASYNC_STREAMING", "BLOCKING_STREAMING"})
    void streamingReaderNotLimited(final RouterApi api) throws Exception {
        // A Publisher<Buffer> reader is streaming, so an over-limit body must be accepted rather than rejected. Only
        // the streaming router paradigms reach the reader without aggregating first; the aggregated paradigms buffer
        // (and reject) the whole request at the server boundary regardless of the resource's parameter type.
        setUp(api);
        final String body = repeat('x', MAX_PAYLOAD * 4);
        // A streaming response has no Content-Length (chunked), so don't assert one.
        sendAndAssertResponse(post("/echo/stream", body, TEXT_PLAIN), OK, TEXT_PLAIN, is(body), __ -> null);
    }

    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(RouterApi.class)
    void negativeLimitWarnsButServes(final RouterApi api) throws Exception {
        // Warn-only mode: matches DefaultAggregatedPayloadSizeLimitTest.negativeServerLimitWarnsButServes.
        maxAggregatedPayloadSize = -MAX_PAYLOAD;
        setUp(api);
        final String body = repeat('x', MAX_PAYLOAD + 1);
        sendAndAssertResponse(post("/echo", body, TEXT_PLAIN), OK, TEXT_PLAIN, body);
    }

    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(RouterApi.class)
    void disabledLimitServes(final RouterApi api) throws Exception {
        maxAggregatedPayloadSize = 0;
        setUp(api);
        final String body = repeat('x', MAX_PAYLOAD + 1);
        sendAndAssertResponse(post("/echo", body, TEXT_PLAIN), OK, TEXT_PLAIN, body);
    }

    private static String repeat(final char c, final int n) {
        final char[] chars = new char[n];
        Arrays.fill(chars, c);
        return new String(chars);
    }
}
