/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.encoding.api.Identity.identity;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

class RequestConversionTests extends AbstractConversionTest {

    @Nullable
    private StreamingHttpRequest original;

    private void setUp(final Supplier<StreamingHttpRequest> originalSupplier,
                       final Supplier<PayloadInfo> payloadInfoSupplier) {
        setUp(payloadInfoSupplier.get());
        this.original = originalSupplier.get();
    }

    @SuppressWarnings("unused")
    private static Stream<Arguments> data() {
        return Stream.of(
            newArguments(() -> new DefaultPayloadInfo(), "no-payload-info"),
            newArguments(() -> new DefaultPayloadInfo().setMayHaveTrailers(true), "trailers"),
            newArguments(() -> new DefaultPayloadInfo().setGenericTypeBuffer(true), "publisher-buffer"),
            newArguments(() -> new DefaultPayloadInfo().setSafeToAggregate(true), "safe-to-aggregate"));
    }

    private static Arguments newArguments(final Supplier<DefaultPayloadInfo> payloadInfoSupplier,
                                          final String paramName) {
        DefaultPayloadInfo payloadInfo = payloadInfoSupplier.get();
        return Arguments.of((Supplier<StreamingHttpRequest>) () -> new DefaultStreamingHttpRequest(GET, "/", HTTP_1_1,
                DefaultHttpHeadersFactory.INSTANCE.newHeaders(), identity(), null, DEFAULT_ALLOCATOR,
                new SingleSubscribePublisher(payloadInfo), payloadInfo, DefaultHttpHeadersFactory.INSTANCE),
                (Supplier<DefaultPayloadInfo>) () -> payloadInfo, paramName);
    }

    @ParameterizedTest(name = "{displayName} [{index}]: name: {2}")
    @MethodSource("data")
    void toAggregated(final Supplier<StreamingHttpRequest> originalSupplier,
                      final Supplier<PayloadInfo> payloadInfoSupplier,
                      @SuppressWarnings("unused") String name) throws Exception {
        setUp(originalSupplier, payloadInfoSupplier);
        convertToAggregated();
    }

    @ParameterizedTest(name = "{displayName} [{index}]: name: {2}")
    @MethodSource("data")
    void toAggregatedToStreaming(final Supplier<StreamingHttpRequest> originalSupplier,
                                 final Supplier<PayloadInfo> payloadInfoSupplier,
                                 @SuppressWarnings("unused") String name) throws Exception {
        setUp(originalSupplier, payloadInfoSupplier);
        verifyConvertedStreamingPayload(convertToAggregated().toStreamingRequest().messageBody());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: name: {2}")
    @MethodSource("data")
    void toBlockingStreaming(final Supplier<StreamingHttpRequest> originalSupplier,
                             final Supplier<PayloadInfo> payloadInfoSupplier,
                             @SuppressWarnings("unused") String name) {
        setUp(originalSupplier, payloadInfoSupplier);
        BlockingStreamingHttpRequest bs = convertToBlockingStreaming();
        // We do not expose trailers from a blocking-streaming entity, so no need to verify here.
        for (Buffer buffer : bs.payloadBody()) {
            verifyPayload(buffer);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}]: name: {2}")
    @MethodSource("data")
    void toBlockingStreamingToStreaming(final Supplier<StreamingHttpRequest> originalSupplier,
                                        Supplier<PayloadInfo> payloadInfoSupplier,
                                        @SuppressWarnings("unused") String name) throws Exception {
        setUp(originalSupplier, payloadInfoSupplier);
        verifyConvertedStreamingPayload(convertToBlockingStreaming().toStreamingRequest().messageBody());
    }

    private HttpRequest convertToAggregated() throws Exception {
        HttpRequest aggr = Objects.requireNonNull(original).toRequest().toFuture().get();
        assertThat("Unexpected request implementation.", aggr, instanceOf(PayloadInfo.class));
        verifyAggregatedPayloadInfo((PayloadInfo) aggr);
        verifyPayload(aggr.payloadBody());
        verifyTrailers(aggr::trailers);

        return aggr;
    }

    private BlockingStreamingHttpRequest convertToBlockingStreaming() {
        BlockingStreamingHttpRequest bs = Objects.requireNonNull(original).toBlockingStreamingRequest();
        assertThat("Unexpected request implementation.", bs, instanceOf(PayloadInfo.class));
        assertThat("Unexpected request implementation.", bs, instanceOf(PayloadInfo.class));
        verifyPayloadInfo((PayloadInfo) bs);

        return bs;
    }
}
