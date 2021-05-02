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
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

class ResponseConversionTests extends AbstractConversionTest {

    @Nullable
    private StreamingHttpResponse original;

    private void setUp(final Supplier<StreamingHttpResponse> originalSupplier,
                       final PayloadInfo payloadInfo) {
        super.setUp(payloadInfo);
        this.original = originalSupplier.get();
    }

    @SuppressWarnings("unused")
    private static Stream<Arguments> data() {
        return Stream.of(
            newParam(new DefaultPayloadInfo(), "no-payload-info"),
            newParam(new DefaultPayloadInfo().setMayHaveTrailers(true), "trailers"),
            newParam(new DefaultPayloadInfo().setGenericTypeBuffer(true), "publisher-buffer"),
            newParam(new DefaultPayloadInfo().setSafeToAggregate(true), "safe-to-aggregate")
        );
    }

    private static Arguments newParam(final DefaultPayloadInfo payloadInfo, final String paramName) {
        return Arguments.of((Supplier<StreamingHttpResponse>) () ->
                new DefaultStreamingHttpResponse(OK, HTTP_1_1,
                        DefaultHttpHeadersFactory.INSTANCE.newHeaders(), DEFAULT_ALLOCATOR,
                        new SingleSubscribePublisher(payloadInfo), payloadInfo, DefaultHttpHeadersFactory.INSTANCE),
                payloadInfo, paramName);
    }

    @ParameterizedTest(name = "{displayName} {index}: name: {2}")
    @MethodSource("data")
    void toAggregated(final Supplier<StreamingHttpResponse> originalSupplier,
                      final PayloadInfo payloadInfo,
                      @SuppressWarnings("unused") String name) throws Exception {
        setUp(originalSupplier, payloadInfo);
        convertToAggregated();
    }

    @ParameterizedTest(name = "{displayName} {index}: name: {2}")
    @MethodSource("data")
    void toAggregatedToStreaming(final Supplier<StreamingHttpResponse> originalSupplier,
                                 final PayloadInfo payloadInfo,
                                 @SuppressWarnings("unused") String name) throws Exception {
        setUp(originalSupplier, payloadInfo);
        verifyConvertedStreamingPayload(convertToAggregated().toStreamingResponse().messageBody());
    }

    @ParameterizedTest(name = "{displayName} {index}: name: {2}")
    @MethodSource("data")
    void toBlockingStreaming(final Supplier<StreamingHttpResponse> originalSupplier,
                             final PayloadInfo payloadInfo,
                             @SuppressWarnings("unused") String name) {
        setUp(originalSupplier, payloadInfo);
        BlockingStreamingHttpResponse bs = convertToBlockingStreaming();
        // We do not expose trailers from a blocking-streaming entity, so no need to verify here.
        for (Buffer buffer : bs.payloadBody()) {
            verifyPayload(buffer);
        }
    }

    @ParameterizedTest(name = "{displayName} {index}: name: {2}")
    @MethodSource("data")
    void toBlockingStreamingToStreaming(final Supplier<StreamingHttpResponse> originalSupplier,
                                        final PayloadInfo payloadInfo,
                                        @SuppressWarnings("unused") String name) throws Exception {

        setUp(originalSupplier, payloadInfo);
        verifyConvertedStreamingPayload(convertToBlockingStreaming().toStreamingResponse().messageBody());
    }

    private HttpResponse convertToAggregated() throws Exception {
        HttpResponse aggr = Objects.requireNonNull(original).toResponse().toFuture().get();

        assertThat("Unexpected response implementation.", aggr, instanceOf(PayloadInfo.class));
        verifyAggregatedPayloadInfo((PayloadInfo) aggr);
        verifyPayload(aggr.payloadBody());
        verifyTrailers(aggr::trailers);

        return aggr;
    }

    private BlockingStreamingHttpResponse convertToBlockingStreaming() {
        BlockingStreamingHttpResponse bs = Objects.requireNonNull(original).toBlockingStreamingResponse();
        assertThat("Unexpected request implementation.", bs, instanceOf(PayloadInfo.class));
        verifyPayloadInfo((PayloadInfo) bs);

        return bs;
    }
}
