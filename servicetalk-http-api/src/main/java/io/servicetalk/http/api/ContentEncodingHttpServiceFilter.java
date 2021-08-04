/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.CharSequences;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.encoding.api.BufferDecoder;
import io.servicetalk.encoding.api.BufferDecoderGroup;
import io.servicetalk.encoding.api.BufferEncoder;
import io.servicetalk.encoding.api.EmptyBufferDecoderGroup;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.regionMatches;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.encoding.api.Identity.identityEncoder;
import static io.servicetalk.encoding.api.internal.HeaderUtils.negotiateAcceptedEncodingRaw;
import static io.servicetalk.http.api.HeaderUtils.addContentEncoding;
import static io.servicetalk.http.api.HttpHeaderNames.ACCEPT_ENCODING;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_ENCODING;
import static io.servicetalk.http.api.HttpRequestMethod.CONNECT;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.HttpResponseStatus.NOT_MODIFIED;
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.INFORMATIONAL_1XX;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SUCCESSFUL_2XX;
import static java.util.Objects.requireNonNull;

/**
 * A {@link StreamingHttpService} that adds encoding / decoding functionality for responses and requests respectively,
 * as these are specified by the spec
 * <a href="https://tools.ietf.org/html/rfc7231#section-3.1.2.2">Content-Encoding</a>.
 *
 * <p>
 * Append this filter before others that are expected to to see compressed content for this request/response, and after
 * other filters that expect to see/manipulate the original payload.
 */
public final class ContentEncodingHttpServiceFilter
        implements StreamingHttpServiceFilterFactory, HttpExecutionStrategyInfluencer {
    private final BufferDecoderGroup decompressors;
    private final List<BufferEncoder> compressors;

    /**
     * Create a new instance and specify the supported compression (matched against
     * {@link HttpHeaderNames#ACCEPT_ENCODING}). The order of entries may impact the selection preference.
     *
     * @param compressors used to compress server responses if client accepts them.
     */
    public ContentEncodingHttpServiceFilter(final List<BufferEncoder> compressors) {
        this(compressors, EmptyBufferDecoderGroup.INSTANCE);
    }

    /**
     * Create a new instance and specify the supported decompression (matched against
     * {@link HttpHeaderNames#CONTENT_ENCODING}) and compression (matched against
     * {@link HttpHeaderNames#ACCEPT_ENCODING}). The order of entries may impact the selection preference.
     *
     * @param compressors used to compress server responses if client accepts them.
     * @param decompressors used to decompress client requests if compressed.
     */
    public ContentEncodingHttpServiceFilter(final List<BufferEncoder> compressors,
                                            final BufferDecoderGroup decompressors) {
        this.decompressors = requireNonNull(decompressors);
        this.compressors = requireNonNull(compressors);
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
        return new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                return Single.defer(() -> {
                    final StreamingHttpRequest requestDecompressed;
                    Iterator<? extends CharSequence> contentEncodingItr =
                            request.headers().valuesIterator(CONTENT_ENCODING);
                    final boolean hasContentEncoding = contentEncodingItr.hasNext();
                    if (hasContentEncoding) {
                        BufferDecoder decoder = matchAndRemoveEncoding(decompressors.decoders(),
                                BufferDecoder::encodingName, contentEncodingItr, request.headers());
                        if (decoder == null) {
                            return succeeded(responseFactory.unsupportedMediaType());
                        }

                        requestDecompressed = request.transformPayloadBody(pub ->
                                decoder.streamingDecoder().deserialize(pub, ctx.executionContext().bufferAllocator()));
                    } else {
                        requestDecompressed = request;
                    }

                    return super.handle(ctx, requestDecompressed, responseFactory).map(response -> {
                        final CharSequence reqAcceptEncoding;
                        if (isPassThrough(request.method(), response) ||
                                (reqAcceptEncoding = request.headers().get(ACCEPT_ENCODING)) == null) {
                            return response;
                        }

                        BufferEncoder encoder = negotiateAcceptedEncodingRaw(reqAcceptEncoding, compressors,
                                BufferEncoder::encodingName);
                        if (encoder == null || identityEncoder().equals(encoder)) {
                            return response;
                        }

                        addContentEncoding(response.headers(), encoder.encodingName());
                        return response.transformPayloadBody(bufPub ->
                                encoder.streamingEncoder().serialize(bufPub, ctx.executionContext().bufferAllocator()));
                    }).subscribeShareContext();
                });
            }
        };
    }

    @Override
    public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
        // No influence - no blocking
        return strategy;
    }

    private static boolean isPassThrough(final HttpRequestMethod method, final StreamingHttpResponse response) {
        // see. https://tools.ietf.org/html/rfc7230#section-3.3.3
        // The length of a message body is determined by one of the following
        //         (in order of precedence):
        //
        // 1.  Any response to a HEAD request and any response with a 1xx
        //         (Informational), 204 (No Content), or 304 (Not Modified) status
        // code is always terminated by the first empty line after the
        // header fields, regardless of the header fields present in the
        // message, and thus cannot contain a message body.
        //
        // 2.  Any 2xx (Successful) response to a CONNECT request implies that
        // the connection will become a tunnel immediately after the empty
        // line that concludes the header fields.  A client MUST ignore any
        // Content-Length or Transfer-Encoding header fields received in
        // such a message.
        // ...
        final int code = response.status().code();
        return INFORMATIONAL_1XX.contains(code) || code == NO_CONTENT.code() || code == NOT_MODIFIED.code() ||
                (method == HEAD || (method == CONNECT && SUCCESSFUL_2XX.contains(code)));
    }

    @Nullable
    static <T> T matchAndRemoveEncoding(final List<T> supportedEncoders,
                                        final Function<T, CharSequence> messageEncodingFunc,
                                        final Iterator<? extends CharSequence> contentEncodingItr,
                                        final HttpHeaders headers) {
        // The order of headers is meaningful for decompression [1], the first header value must be supported or we
        // cannot decode.
        // [1] If one or more encodings have been applied to a representation, the
        //    sender that applied the encodings MUST generate a Content-Encoding
        //    header field that lists the content codings in the order in which
        //    they were applied
        // https://datatracker.ietf.org/doc/html/rfc7231#section-3.1.2.2
        if (supportedEncoders.isEmpty() || !contentEncodingItr.hasNext()) {
            return null;
        }
        final CharSequence encoding = contentEncodingItr.next();
        int jNonTrimmed;
        int i = 0;
        int j = CharSequences.indexOf(encoding, ',', 0);
        if (j < 0) {
            j = encoding.length();
        }

        if (j == 0) {
            return null;
        }

        jNonTrimmed = j;
        // Trim spaces from end.
        while (encoding.charAt(j - 1) == ' ') {
            if (--j == 0) {
                return null;
            }
        }

        // Trim spaces from beginning.
        while (encoding.charAt(i) == ' ') {
            if (++i == j) {
                return null;
            }
        }

        // If the accepted encoding is supported, use it.
        int x = 0;
        do {
            T supportedEncoding = supportedEncoders.get(x);
            CharSequence serverSupported = messageEncodingFunc.apply(supportedEncoding);
            // Use serverSupported.length() as we ignore qvalue prioritization for now.
            // All content-coding values are case-insensitive [1].
            // [1] https://datatracker.ietf.org/doc/html/rfc7231#section-3.1.2.1.
            if (regionMatches(encoding, true, i, serverSupported, 0, serverSupported.length())) {
                // We will be stripping the encoding, so remove the header.
                contentEncodingItr.remove();
                if (jNonTrimmed + 1 < encoding.length()) {
                    // Order of content-encodings must be preserved, so if the value is CSV add back the remainder of
                    // the unused value.
                    resetContentEncoding(headers, encoding.subSequence(jNonTrimmed + 1, encoding.length()));
                }

                return supportedEncoding;
            }
        } while (++x < supportedEncoders.size());

        return null;
    }

    private static void resetContentEncoding(HttpHeaders headers, CharSequence updatedValue) {
        List<CharSequence> valuesArray = new ArrayList<>(4);
        valuesArray.add(updatedValue);
        for (CharSequence value : headers.values(CONTENT_ENCODING)) {
            valuesArray.add(value);
        }
        headers.set(CONTENT_ENCODING, valuesArray);
    }
}
