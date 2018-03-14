/**
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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.FlushStrategy;
import io.servicetalk.transport.netty.internal.Connection.RequestNSupplier;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Contract for using a {@link Connection} to make pipelined requests, typically for a client. <p>
 *     Pipelining allows to have concurrent requests processed on the server but still deliver responses in order.
 *     This eliminates the need for request-response correlation, at the cost of head-of-line blocking.
 * @param <Req> Type of requests sent on this connection.
 * @param <Resp> Type of responses read from this connection.
 */
public interface PipelinedConnection<Req, Resp> extends ConnectionContext {

    /**
     * Writes a {@link Req} object on this connection.<p>
     *     Use {@link #request(Object, Supplier)} to override the predicate used to mark the end of response.
     *
     * @param request to write.
     * @return Response {@link Publisher} for this request.
     */
    Publisher<Resp> request(Req request);

    /**
     * Writes on this connection a request encapsulated in the passed {@link Writer}.<p>
     *     Use {@link #request(Writer, Supplier)} to override the predicate used to mark the end of response.
     *
     * @param writer to write the request.
     * @return Response {@link Publisher} for this request.
     */
    Publisher<Resp> request(Writer writer);

    /**
     * Writes on this connection a request encapsulated in the passed {@link Writer}.<p>
     *
     * @param writer to write the request.
     * @param terminalMsgPredicateSupplier {@link Supplier} of a dynamic {@link Predicate} for this request that will mark the end of the response.
     * @return Response {@link Publisher} for this request.
     */
    Publisher<Resp> request(Writer writer, Supplier<Predicate<Resp>> terminalMsgPredicateSupplier);

    /**
     * Writes a {@link Req} object on this connection.
     *
     * @param request to write.
     * @param terminalMsgPredicateSupplier {@link Supplier} of a dynamic {@link Predicate} for this request that will mark the end of the response.
     * @return Response {@link Publisher} for this request.
     */
    Publisher<Resp> request(Req request, Supplier<Predicate<Resp>> terminalMsgPredicateSupplier);

    /**
     * Send request produced by a {@link Single} on this connection.
     * <p>
     *     Use {@link #request(Single, Supplier)} to override the predicate used to mark the end of response.
     *
     * @param request to write.
     * @return Response {@link Publisher} for this request.
     */
    Publisher<Resp> request(Single<Req> request);

    /**
     * Send request produced by a {@link Single} on this connection.
     *
     * @param request {@link Single} producing the request to write.
     * @param terminalMsgPredicateSupplier {@link Supplier} of a dynamic {@link Predicate} for this request that will mark the end of the response.
     * @return Response {@link Publisher} for this request.
     */
    Publisher<Resp> request(Single<Req> request, Supplier<Predicate<Resp>> terminalMsgPredicateSupplier);

    /**
     * Send request(s) produced by a {@link Publisher} on this connection.
     * <p>
     *     Use {@link #request(Publisher, Supplier, FlushStrategy)} to override the predicate used to mark the end of response.
     *
     * @param request {@link Publisher} producing the request(s) to write.
     * @param flushStrategy {@link FlushStrategy} to use for flushing the requests written.
     * @return Response {@link Publisher} for this request.
     */
    Publisher<Resp> request(Publisher<Req> request, FlushStrategy flushStrategy);

    /**
     * Send request(s) produced by a {@link Publisher} on this connection.
     *
     * @param request {@link Publisher} producing the request(s) to write.
     * @param flushStrategy {@link FlushStrategy} to use for flushing the requests written.
     * @param terminalMsgPredicateSupplier {@link Supplier} of a dynamic {@link Predicate} for this request that will mark the end of the response.
     * @return Response {@link Publisher} for this request.
     */
    Publisher<Resp> request(Publisher<Req> request, Supplier<Predicate<Resp>> terminalMsgPredicateSupplier, FlushStrategy flushStrategy);

    /**
     * Send request(s) produced by a {@link Publisher} on this connection.
     * <p>
     *     Use {@link #request(Publisher, FlushStrategy, Supplier, Supplier)} to override the predicate used to mark the end of response.
     *
     * @param request {@link Publisher} producing the request(s) to write.
     * @param flushStrategy {@link FlushStrategy} to use for flushing the requests written.
     * @param requestNSupplierFactory A {@link Supplier} of {@link RequestNSupplier} for this request.
     * @return Response {@link Publisher} for this request.
     */
    Publisher<Resp> request(Publisher<Req> request, FlushStrategy flushStrategy, Supplier<RequestNSupplier> requestNSupplierFactory);

    /**
     * Send request(s) produced by a {@link Publisher} on this connection.
     *
     * @param request {@link Publisher} producing the request(s) to write.
     * @param flushStrategy {@link FlushStrategy} to use for flushing the requests written.
     * @param requestNSupplierFactory A {@link Supplier} of {@link RequestNSupplier} for this request.
     * @param terminalMsgPredicateSupplier {@link Supplier} of a dynamic {@link Predicate} for this request that will mark the end of the response.
     * @return Response {@link Publisher} for this request.
     */
    Publisher<Resp> request(Publisher<Req> request, FlushStrategy flushStrategy, Supplier<RequestNSupplier> requestNSupplierFactory,
                            Supplier<Predicate<Resp>> terminalMsgPredicateSupplier);

    /**
     * A writer to write a request on a connection. <p>
     *     The order in which {@link #write()} is invoked is the order in which the items will be written on the connection,
     *     so, this can be used to make decisions based on ordering, if required.
     * <h2>Thread safety.</h2>
     * {@link #write()} will never be invoked concurrently. Any state only visible to this method over multiple invocations
     * can assume memory visibility.
     */
    @FunctionalInterface
    interface Writer {

        /**
         * Writes request associated with this writer.
         * @return {@link Completable} for the write.
         */
        Completable write();
    }
}
