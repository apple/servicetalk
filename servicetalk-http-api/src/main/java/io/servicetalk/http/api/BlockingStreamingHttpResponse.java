/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.api.Publisher;

import org.reactivestreams.Subscriber;

import java.util.function.Function;

/**
 * The equivalent of {@link StreamingHttpResponse} but with synchronous/blocking APIs instead of asynchronous APIs.
 *
 * @param <T> Type of payload.
 */
public interface BlockingStreamingHttpResponse<T> extends HttpResponseMetaData {
    @Override
    BlockingStreamingHttpResponse<T> setVersion(HttpProtocolVersion version);

    @Override
    BlockingStreamingHttpResponse<T> setStatus(HttpResponseStatus status);

    /**
     * The <a href="https://tools.ietf.org/html/rfc7230.html#section-3.3">HTTP Payload Body</a>.
     * <p>
     * By default the returned {@link Iterable} only supports a single call to {@link Iterable#iterator()}. This is
     * because the payload is typically not all available in memory at any given time. If you need multiple calls to
     * {@link Iterable#iterator()} you should add support for caching data in memory and enable multiple
     * {@link Publisher#subscribe(Subscriber)} calls. See the
     * <a href="http://reactivex.io/documentation/operators/replay.html">Replay Operator</a> and
     * {@link Publisher#multicast(int) Multicast Operator} for more details.
     *
     * @return {@link Iterable} that emits the
     * <a href="https://tools.ietf.org/html/rfc7230.html#section-3.3">HTTP Payload Body</a> of this request.
     */
    // TODO(scott): add a link in the javadoc to ServiceTalk replay operator and synchronous equivalent tools.
    BlockingIterable<T> getPayloadBody();

    /**
     * To modify the {@link #getPayloadBody()} of the response and preserving the containing response object.
     *
     * @param transformer {@link Function} which converts the payload body to another type.
     * @param <R> Type of the resulting payload body.
     * @return New {@link BlockingStreamingHttpResponse} with the altered {@link #getPayloadBody()}.
     */
    <R> BlockingStreamingHttpResponse<R> transformPayloadBody(
            Function<BlockingIterable<T>, BlockingIterable<R>> transformer);
}
