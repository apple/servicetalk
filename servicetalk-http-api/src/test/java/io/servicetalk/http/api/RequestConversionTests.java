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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.encoding.api.ContentCodec.IDENTITY;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

@RunWith(Parameterized.class)
public class RequestConversionTests extends AbstractConversionTest {

    private final StreamingHttpRequest original;

    public RequestConversionTests(final Supplier<StreamingHttpRequest> originalSupplier, PayloadInfo payloadInfo,
                                  @SuppressWarnings("unused") String name) {
        super(payloadInfo);
        this.original = originalSupplier.get();
    }

    @Parameterized.Parameters(name = "{index}: name: {2}")
    public static List<Object[]> data() {
        List<Object[]> params = new ArrayList<>();
        params.add(newParam(new DefaultPayloadInfo(), "no-payload-info"));
        params.add(newParam(new DefaultPayloadInfo().setMayHaveTrailers(true), "trailers"));
        params.add(newParam(new DefaultPayloadInfo().setGenericTypeBuffer(true), "publisher-buffer"));
        params.add(newParam(new DefaultPayloadInfo().setSafeToAggregate(true), "safe-to-aggregate"));
        return params;
    }

    private static Object[] newParam(final DefaultPayloadInfo payloadInfo, final String paramName) {
        return new Object[]{(Supplier<StreamingHttpRequest>) () -> new DefaultStreamingHttpRequest(GET, "/", HTTP_1_1,
                DefaultHttpHeadersFactory.INSTANCE.newHeaders(), IDENTITY, DEFAULT_ALLOCATOR,
                new SingleSubscribePublisher(payloadInfo), payloadInfo, DefaultHttpHeadersFactory.INSTANCE),
                payloadInfo, paramName};
    }

    @Test
    public void toAggregated() throws Exception {
        convertToAggregated();
    }

    @Test
    public void toAggregatedToStreaming() throws Exception {
        verifyConvertedStreamingPayload(convertToAggregated().toStreamingRequest().messageBody());
    }

    @Test
    public void toBlockingStreaming() {
        BlockingStreamingHttpRequest bs = convertToBlockingStreaming();
        // We do not expose trailers from a blocking-streaming entity, so no need to verify here.
        for (Buffer buffer : bs.payloadBody()) {
            verifyPayload(buffer);
        }
    }

    @Test
    public void toBlockingStreamingToStreaming() throws Exception {
        verifyConvertedStreamingPayload(convertToBlockingStreaming().toStreamingRequest().messageBody());
    }

    private HttpRequest convertToAggregated() throws Exception {
        HttpRequest aggr = original.toRequest().toFuture().get();
        assertThat("Unexpected request implementation.", aggr, instanceOf(PayloadInfo.class));
        verifyAggregatedPayloadInfo((PayloadInfo) aggr);
        verifyPayload(aggr.payloadBody());
        verifyTrailers(aggr::trailers);

        return aggr;
    }

    private BlockingStreamingHttpRequest convertToBlockingStreaming() {
        BlockingStreamingHttpRequest bs = original.toBlockingStreamingRequest();
        assertThat("Unexpected request implementation.", bs, instanceOf(PayloadInfo.class));
        assertThat("Unexpected request implementation.", bs, instanceOf(PayloadInfo.class));
        verifyPayloadInfo((PayloadInfo) bs);

        return bs;
    }
}
