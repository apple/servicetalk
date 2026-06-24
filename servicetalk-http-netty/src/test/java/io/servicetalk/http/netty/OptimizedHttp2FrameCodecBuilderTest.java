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
package io.servicetalk.http.netty;

import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2FrameCodec;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

class OptimizedHttp2FrameCodecBuilderTest {

    private static final String EMPTY_DATA_FRAME_DECODER =
            "io.netty.handler.codec.http2.Http2EmptyDataFrameConnectionDecoder";
    private static final String MAX_RST_FRAME_DECODER = "io.netty.handler.codec.http2.Http2MaxRstFrameDecoder";

    @Test
    void serverReAppliesDdosProtectionDecoders() {
        final List<String> chain = decoderChain(true);
        // Netty's buildFromCodec wraps the decoder with these DDoS guards; our build() override rebuilds the decoder to
        // install ServiceTalkHttp2HeadersDecoder, so we must re-apply them. Without the fix these are absent.
        assertThat(chain, hasItem(EMPTY_DATA_FRAME_DECODER));
        assertThat(chain, hasItem(MAX_RST_FRAME_DECODER));
    }

    @Test
    void clientOnlyAppliesEmptyDataFrameDecoder() {
        final List<String> chain = decoderChain(false);
        // The empty-DATA-frame guard applies to both peers; RST_STREAM rate-limiting is server-only (client uses 0/0).
        assertThat(chain, hasItem(EMPTY_DATA_FRAME_DECODER));
        assertThat(chain, not(hasItem(MAX_RST_FRAME_DECODER)));
    }

    private static List<String> decoderChain(final boolean server) {
        final Http2FrameCodec codec = OptimizedHttp2FrameCodecBuilder.newBuilder(server, h2Default()).build();
        final List<String> classNames = new ArrayList<>();
        Http2ConnectionDecoder decoder = codec.decoder();
        while (decoder != null) {
            classNames.add(decoder.getClass().getName());
            decoder = unwrap(decoder);
        }
        return classNames;
    }

    @Nullable
    private static Http2ConnectionDecoder unwrap(final Http2ConnectionDecoder decoder) {
        // DecoratingHttp2ConnectionDecoder holds its delegate in a private field; walk it reflectively so the test does
        // not depend on Netty exposing the decorator chain.
        Class<?> clazz = decoder.getClass();
        while (clazz != null) {
            if ("io.netty.handler.codec.http2.DecoratingHttp2ConnectionDecoder".equals(clazz.getName())) {
                try {
                    final Field delegate = clazz.getDeclaredField("delegate");
                    delegate.setAccessible(true);
                    return (Http2ConnectionDecoder) delegate.get(decoder);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("Unable to read DecoratingHttp2ConnectionDecoder#delegate", e);
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }
}
