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
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.encoding.api.ContentCodec;

import java.io.OutputStream;

/**
 * The equivalent of {@link HttpResponse} but provides an ability to write the payload to an {@link HttpPayloadWriter}.
 *
 * @see BlockingStreamingHttpService
 */
public interface BlockingStreamingHttpServerResponse extends HttpResponseMetaData {
    /**
     * Sends the {@link HttpResponseMetaData} and returns an {@link HttpPayloadWriter} to continue writing the payload
     * body.
     * <p>
     * <b>Note:</b> calling any other method on this class after calling this method is not allowed.
     *
     * @return {@link HttpPayloadWriter} to write a payload body
     * @throws IllegalStateException if one of the {@code sendMetaData*} methods has been called on this response
     */
    HttpPayloadWriter<Buffer> sendMetaData();

    /**
     * Sends the {@link HttpResponseMetaData} to the client and returns an {@link HttpPayloadWriter} of type {@link T}
     * to continue writing a payload body. Each element will be serialized using provided {@code serializer}.
     * <p>
     * <b>Note:</b> calling any other method on this class after calling this method is not allowed.
     * @param serializer used to serialize the payload elements
     * @param <T> the type of objects to write
     * @return {@link HttpPayloadWriter} to write a payload body
     * @throws IllegalStateException if one of the {@code sendMetaData*} methods has been called on this response
     * @deprecated Use {@link #sendMetaData(HttpStreamingSerializer)}.
     */
    @Deprecated
    default <T> HttpPayloadWriter<T> sendMetaData(HttpSerializer<T> serializer) {
        throw new UnsupportedOperationException("BlockingStreamingHttpServerResponse#sendMetaData(HttpSerializer) " +
                "is not supported by " + getClass() + ". This method is deprecated, consider migrating to " +
                "BlockingStreamingHttpServerResponse#sendMetaData(HttpStreamingSerializer) or implement this " +
                "method if it's required temporarily.");
    }

    /**
     * Sends the {@link HttpResponseMetaData} to the client and returns an {@link HttpPayloadWriter} of type {@link T}
     * to continue writing a payload body. Each element will be serialized using provided {@code serializer}.
     * <p>
     * <b>Note:</b> calling any other method on this class after calling this method is not allowed.
     *
     * @param serializer used to serialize the payload elements
     * @param <T> the type of objects to write
     * @return {@link HttpPayloadWriter} to write a payload body
     * @throws IllegalStateException if one of the {@code sendMetaData*} methods has been called on this response
     */
    <T> HttpPayloadWriter<T> sendMetaData(HttpStreamingSerializer<T> serializer);

    /**
     * Sends the {@link HttpResponseMetaData} to the client and returns an {@link OutputStream} to continue writing a
     * payload body.
     * <p>
     * <b>Note:</b> calling any other method on this class after calling this method is not allowed.
     *
     * @return {@link HttpOutputStream} to write a payload body
     * @throws IllegalStateException if one of the {@code sendMetaData*} methods has been called on this response
     */
    HttpOutputStream sendMetaDataOutputStream();

    @Override
    BlockingStreamingHttpServerResponse version(HttpProtocolVersion version);

    @Override
    BlockingStreamingHttpServerResponse status(HttpResponseStatus status);

    @Override
    default BlockingStreamingHttpServerResponse encoding(ContentCodec encoding) {
        throw new UnsupportedOperationException("BlockingStreamingHttpServerResponse#encoding(ContentCodec) is not " +
                "supported by " + getClass() + ". This method is deprecated, consider migrating to provided " +
                "alternatives or implement this method if it's required temporarily.");
    }

    @Override
    default BlockingStreamingHttpServerResponse addHeader(final CharSequence name, final CharSequence value) {
        HttpResponseMetaData.super.addHeader(name, value);
        return this;
    }

    @Override
    default BlockingStreamingHttpServerResponse addHeaders(final HttpHeaders headers) {
        HttpResponseMetaData.super.addHeaders(headers);
        return this;
    }

    @Override
    default BlockingStreamingHttpServerResponse setHeader(final CharSequence name, final CharSequence value) {
        HttpResponseMetaData.super.setHeader(name, value);
        return this;
    }

    @Override
    default BlockingStreamingHttpServerResponse setHeaders(final HttpHeaders headers) {
        HttpResponseMetaData.super.setHeaders(headers);
        return this;
    }

    @Override
    default BlockingStreamingHttpServerResponse addCookie(final HttpCookiePair cookie) {
        HttpResponseMetaData.super.addCookie(cookie);
        return this;
    }

    @Override
    default BlockingStreamingHttpServerResponse addCookie(final CharSequence name, final CharSequence value) {
        HttpResponseMetaData.super.addCookie(name, value);
        return this;
    }

    @Override
    default BlockingStreamingHttpServerResponse addSetCookie(final HttpSetCookie cookie) {
        HttpResponseMetaData.super.addSetCookie(cookie);
        return this;
    }

    @Override
    default BlockingStreamingHttpServerResponse addSetCookie(final CharSequence name, final CharSequence value) {
        HttpResponseMetaData.super.addSetCookie(name, value);
        return this;
    }

    @Override
    BlockingStreamingHttpServerResponse context(ContextMap context);
}
