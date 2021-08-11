/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.CloseableIterable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.encoding.api.BufferEncoder;
import io.servicetalk.encoding.api.ContentCodec;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.defer;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.fromInputStream;
import static io.servicetalk.concurrent.api.Publisher.fromIterable;
import static io.servicetalk.http.api.BlockingStreamingHttpMessageBodyUtils.newMessageBody;

final class DefaultBlockingStreamingHttpRequest extends AbstractDelegatingHttpRequest
        implements BlockingStreamingHttpRequest {

    DefaultBlockingStreamingHttpRequest(final DefaultStreamingHttpRequest original) {
        super(original);
    }

    @Override
    public BlockingStreamingHttpRequest version(final HttpProtocolVersion version) {
        original.version(version);
        return this;
    }

    @Deprecated
    @Override
    public BlockingStreamingHttpRequest encoding(final ContentCodec encoding) {
        original.encoding(encoding);
        return this;
    }

    @Override
    public BlockingStreamingHttpRequest contentEncoding(@Nullable final BufferEncoder encoder) {
        original.contentEncoding(encoder);
        return this;
    }

    @Override
    public BlockingStreamingHttpRequest method(final HttpRequestMethod method) {
        original.method(method);
        return this;
    }

    @Override
    public BlockingStreamingHttpRequest requestTarget(final String requestTarget) {
        original.requestTarget(requestTarget);
        return this;
    }

    @Override
    public BlockingStreamingHttpRequest requestTarget(final String requestTarget, final Charset encoding) {
        original.requestTarget(requestTarget, encoding);
        return this;
    }

    @Override
    public BlockingStreamingHttpRequest path(final String path) {
        original.path(path);
        return this;
    }

    @Override
    public BlockingStreamingHttpRequest appendPathSegments(final String... segments) {
        original.appendPathSegments(segments);
        return this;
    }

    @Override
    public BlockingStreamingHttpRequest rawPath(final String path) {
        original.rawPath(path);
        return this;
    }

    @Override
    public BlockingStreamingHttpRequest rawQuery(@Nullable final String query) {
        original.rawQuery(query);
        return this;
    }

    @Override
    public BlockingStreamingHttpRequest query(@Nullable final String query) {
        original.query(query);
        return this;
    }

    @Override
    public BlockingStreamingHttpRequest addQueryParameter(String key, String value) {
        original.addQueryParameter(key, value);
        return this;
    }

    @Override
    public BlockingStreamingHttpRequest addQueryParameters(String key, Iterable<String> values) {
        original.addQueryParameters(key, values);
        return this;
    }

    @Override
    public BlockingStreamingHttpRequest addQueryParameters(String key, String... values) {
        original.addQueryParameters(key, values);
        return this;
    }

    @Override
    public BlockingStreamingHttpRequest setQueryParameter(String key, String value) {
        original.setQueryParameter(key, value);
        return this;
    }

    @Override
    public BlockingStreamingHttpRequest setQueryParameters(String key, Iterable<String> values) {
        original.setQueryParameters(key, values);
        return this;
    }

    @Override
    public BlockingStreamingHttpRequest setQueryParameters(String key, String... values) {
        original.setQueryParameters(key, values);
        return this;
    }

    @Override
    public BlockingIterable<Buffer> payloadBody() {
        return original.payloadBody().toIterable();
    }

    @Override
    public <T> BlockingIterable<T> payloadBody(final HttpStreamingDeserializer<T> deserializer) {
        return deserializer.deserialize(headers(), payloadBody(), original.payloadHolder().allocator());
    }

    @Override
    public HttpMessageBodyIterable<Buffer> messageBody() {
        return newMessageBody(original.messageBody().toIterable());
    }

    @Override
    public <T> HttpMessageBodyIterable<T> messageBody(final HttpStreamingDeserializer<T> deserializer) {
        return newMessageBody(original.messageBody().toIterable(), headers(), deserializer,
                original.payloadHolder().allocator());
    }

    @Override
    public BlockingStreamingHttpRequest payloadBody(final Iterable<Buffer> payloadBody) {
        original.payloadBody(fromIterable(payloadBody));
        return this;
    }

    @Override
    public BlockingStreamingHttpRequest payloadBody(final CloseableIterable<Buffer> payloadBody) {
        original.payloadBody(fromIterable(payloadBody));
        return this;
    }

    @Override
    public BlockingStreamingHttpRequest payloadBody(final InputStream payloadBody) {
        original.payloadBody(fromInputStream(payloadBody)
                .map(bytes -> original.payloadHolder().allocator().wrap(bytes)));
        return this;
    }

    @Override
    public BlockingStreamingHttpRequest messageBody(final HttpMessageBodyIterable<Buffer> messageBody) {
        original.payloadHolder().messageBody(defer(() -> {
            HttpMessageBodyIterator<Buffer> body = messageBody.iterator();
            return fromIterable(() -> body)
                    .map(o -> (Object) o)
                    .concat(defer(() -> from(body.trailers()).filter(Objects::nonNull)));
        }));
        return this;
    }

    @Override
    public <T> BlockingStreamingHttpRequest messageBody(final HttpMessageBodyIterable<T> messageBody,
                                                        final HttpStreamingSerializer<T> serializer) {
        original.payloadHolder().messageBody(defer(() -> {
            HttpMessageBodyIterator<T> body = messageBody.iterator();
            return from(serializer.serialize(headers(), () -> body, original.payloadHolder().allocator()))
                    .map(o -> (Object) o)
                    .concat(defer(() -> from(body.trailers()).filter(Objects::nonNull)));
        }));
        return this;
    }

    @Deprecated
    @Override
    public <T> BlockingStreamingHttpRequest payloadBody(final Iterable<T> payloadBody,
                                                        final HttpSerializer<T> serializer) {
        original.payloadBody(fromIterable(payloadBody), serializer);
        return this;
    }

    @Override
    public <T> BlockingStreamingHttpRequest payloadBody(final Iterable<T> payloadBody,
                                                        final HttpStreamingSerializer<T> serializer) {
        original.payloadBody(fromIterable(payloadBody), serializer);
        return this;
    }

    @Deprecated
    @Override
    public <T> BlockingStreamingHttpRequest payloadBody(final CloseableIterable<T> payloadBody,
                                                        final HttpSerializer<T> serializer) {
        original.payloadBody(fromIterable(payloadBody), serializer);
        return this;
    }

    @Deprecated
    @Override
    public <T> BlockingStreamingHttpRequest transformPayloadBody(
            final Function<BlockingIterable<Buffer>, BlockingIterable<T>> transformer,
            final HttpSerializer<T> serializer) {
        original.transformPayloadBody(bufferPublisher -> fromIterable(transformer.apply(bufferPublisher.toIterable())),
                serializer);
        return this;
    }

    @Override
    public BlockingStreamingHttpRequest transformPayloadBody(
            final UnaryOperator<BlockingIterable<Buffer>> transformer) {
        original.transformPayloadBody(bufferPublisher -> fromIterable(transformer.apply(bufferPublisher.toIterable())));
        return this;
    }

    @Deprecated
    @Override
    public <T> BlockingStreamingHttpRequest transform(final TrailersTransformer<T, Buffer> trailersTransformer) {
        original.transform(trailersTransformer);
        return this;
    }

    @Override
    public Single<HttpRequest> toRequest() {
        return toStreamingRequest().toRequest();
    }

    @Override
    public StreamingHttpRequest toStreamingRequest() {
        return original;
    }
}
