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
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.utils.PayloadSizeLimitingHttpServiceFilter;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;

import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.PAYLOAD_TOO_LARGE;
import static java.util.Collections.singleton;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

/**
 * Documents how request payload sizes are bounded for JAX-RS resources: entity readers that buffer the body inside
 * Jersey are not covered by the server's {@code maxAggregatedPayloadSize}, so
 * {@link PayloadSizeLimitingHttpServiceFilter} is the supported way to express a limit.
 */
class RequestPayloadSizeLimitTest extends AbstractJerseyStreamingHttpServiceTest {

    private static final int MAX_PAYLOAD = 16;

    // Applied to the server via configureBuilders(); cleared by the tests that show what the limit does not cover.
    private boolean limitFilter = true;

    @Path("/echo")
    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @SuppressWarnings("PMD.PublicMemberInNonPublicType") // JAX-RS resource must be public
    public static class EchoResource {
        // Jersey's built-in String reader buffers the whole entity inside Jersey.
        @POST
        @Path("/string")
        public String echoString(final String body) {
            return body;
        }

        // Single<Buffer> is aggregated too, but by a ServiceTalk reader.
        @POST
        public Single<Buffer> echo(final Single<Buffer> body) {
            return body;
        }

        // Consumes the stream incrementally and only then responds, so a rejection mid-body is still visible in the
        // response status (an echoing resource commits its status before the body has been read).
        @POST
        @Path("/stream/count")
        public Single<String> countStream(final Publisher<Buffer> body) {
            return body.collect(() -> 0, (total, buffer) -> total + buffer.readableBytes()).map(String::valueOf);
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
    void configureBuilders(final HttpServerBuilder serverBuilder,
                           final HttpJerseyRouterBuilder jerseyRouterBuilder) {
        super.configureBuilders(serverBuilder, jerseyRouterBuilder);
        if (limitFilter) {
            // The recipe the documentation recommends: the filter is the only limit.
            serverBuilder.maxAggregatedPayloadSize(0)
                    .appendServiceFilter(new PayloadSizeLimitingHttpServiceFilter(MAX_PAYLOAD));
        } else {
            serverBuilder.maxAggregatedPayloadSize(MAX_PAYLOAD);
        }
    }

    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(RouterApi.class)
    void withinLimitSucceeds(final RouterApi api) throws Exception {
        setUp(api);
        final String body = repeat('x', MAX_PAYLOAD);
        sendAndAssertResponse(post("/echo/string", body, TEXT_PLAIN), OK, TEXT_PLAIN, body);
    }

    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(value = RouterApi.class, names = {"ASYNC_STREAMING"})
    void filterRejectsOversizedContentLength(final RouterApi api) throws Exception {
        // A declared Content-Length over the limit is refused before the body is read, so the router is never
        // involved; one paradigm is enough to cover it.
        setUp(api);
        assertOverLimitRejected(oversizedRequest("/echo/string"));
    }

    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(value = RouterApi.class, names = {"ASYNC_STREAMING", "BLOCKING_STREAMING"})
    void filterBoundsBodyBufferedByJersey(final RouterApi api) throws Exception {
        // Without a declared length the filter counts bytes as they arrive, so this exercises the limit while
        // Jersey's String reader is buffering the entity -- the case maxAggregatedPayloadSize does not cover.
        setUp(api);
        assertOverLimitRejected(chunked(oversizedRequest("/echo/string")));
    }

    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(value = RouterApi.class, names = {"ASYNC_STREAMING", "BLOCKING_STREAMING"})
    void filterBoundsStreamingReader(final RouterApi api) throws Exception {
        // A Publisher<Buffer> resource never aggregates, so the filter is the only thing that bounds it.
        setUp(api);
        assertOverLimitRejected(chunked(oversizedRequest("/echo/stream/count")));
    }

    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(value = RouterApi.class, names = {"ASYNC_STREAMING", "BLOCKING_STREAMING"})
    void withoutFilterStreamingReaderIsUnbounded(final RouterApi api) throws Exception {
        // The counterpart to the test above: nothing bounds a streamed body when the filter is absent.
        limitFilter = false;
        setUp(api);
        final int size = MAX_PAYLOAD * 4;
        sendAndAssertResponse(post("/echo/stream/count", repeat('x', size), TEXT_PLAIN),
                OK, TEXT_PLAIN, Integer.toString(size));
    }

    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(value = RouterApi.class, names = {"ASYNC_STREAMING", "BLOCKING_STREAMING"})
    void withoutFilterJerseyAggregationIsUnbounded(final RouterApi api) throws Exception {
        // Jersey buffers the String entity itself, which maxAggregatedPayloadSize does not cover: without the filter an
        // oversized body is served. This is why the filter is the documented way to limit payload sizes.
        limitFilter = false;
        setUp(api);
        final String body = repeat('x', MAX_PAYLOAD * 4);
        sendAndAssertResponse(post("/echo/string", body, TEXT_PLAIN), OK, TEXT_PLAIN, body);
    }

    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(value = RouterApi.class, names = {"ASYNC_STREAMING", "BLOCKING_STREAMING"})
    void withoutFilterServiceTalkReaderIsUnbounded(final RouterApi api) throws Exception {
        // Single<Buffer> is a ServiceTalk reader, but under a streaming router it aggregates inside the router rather
        // than in the transport, so maxAggregatedPayloadSize does not reach it either.
        limitFilter = false;
        setUp(api);
        final String body = repeat('x', MAX_PAYLOAD * 4);
        sendAndAssertResponse(post("/echo", body, TEXT_PLAIN), OK, TEXT_PLAIN, body);
    }

    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(value = RouterApi.class, names = {"ASYNC_AGGREGATED", "BLOCKING_AGGREGATED"})
    void withoutFilterServiceTalkAggregationIsBounded(final RouterApi api) throws Exception {
        // The aggregated paradigms aggregate in the transport, where maxAggregatedPayloadSize does apply.
        limitFilter = false;
        setUp(api);
        assertOverLimitRejected(oversizedRequest("/echo"));
    }

    private StreamingHttpRequest oversizedRequest(final String path) {
        return post(path, repeat('x', MAX_PAYLOAD + 1), TEXT_PLAIN);
    }

    // Drops Content-Length so the body is sent chunked and has to be counted as it arrives, rather than being
    // refused up front by the filter's declared-length check. The transport does not re-add the header: it can only
    // do so for a payload it knows is safe to aggregate, which a streaming client request never is.
    private static StreamingHttpRequest chunked(final StreamingHttpRequest request) {
        request.headers().remove(CONTENT_LENGTH);
        return request;
    }

    private void assertOverLimitRejected(final StreamingHttpRequest request) {
        try {
            sendAndAssertStatusOnly(request, PAYLOAD_TOO_LARGE);
        } catch (RuntimeException e) {
            // Rejecting an oversized body cancels the in-flight upload, so the connection teardown often races ahead
            // of the mapped 413. Both mean the payload was rejected, but nothing else does.
            if (!hasCause(e, IOException.class)) {
                throw e;
            }
        }
    }

    private static boolean hasCause(Throwable t, final Class<? extends Throwable> type) {
        for (; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    private static String repeat(final char c, final int n) {
        final char[] chars = new char[n];
        Arrays.fill(chars, c);
        return new String(chars);
    }
}
