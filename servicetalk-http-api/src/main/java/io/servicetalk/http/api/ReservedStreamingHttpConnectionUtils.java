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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.DelegatingListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import static io.servicetalk.http.api.HttpApiConversions.toReservedBlockingConnection;
import static io.servicetalk.http.api.HttpApiConversions.toReservedBlockingStreamingConnection;
import static io.servicetalk.http.api.HttpApiConversions.toReservedConnection;

@Deprecated
final class ReservedStreamingHttpConnectionUtils {  // FIXME: 0.43 - remove deprecated class

    private ReservedStreamingHttpConnectionUtils() {
        // No instances
    }

    static ReservedStreamingHttpConnection toReservedStreamingHttpConnection(final StreamingHttpConnection connection) {
        if (connection instanceof ReservedStreamingHttpConnection) {
            return (ReservedStreamingHttpConnection) connection;
        }
        return new ToReservedStreamingHttpConnection(connection);
    }

    private static final class ToReservedStreamingHttpConnection
            extends DelegatingListenableAsyncCloseable<StreamingHttpConnection>
            implements ReservedStreamingHttpConnection {

        ToReservedStreamingHttpConnection(final StreamingHttpConnection delegate) {
            super(delegate);
        }

        @Override
        public Completable releaseAsync() {
            return Completable.completed(); // nothing to release
        }

        @Override
        public ReservedHttpConnection asConnection() {
            return toReservedConnection(this, executionContext().executionStrategy());
        }

        @Override
        public ReservedBlockingStreamingHttpConnection asBlockingStreamingConnection() {
            return toReservedBlockingStreamingConnection(this, executionContext().executionStrategy());
        }

        @Override
        public ReservedBlockingHttpConnection asBlockingConnection() {
            return toReservedBlockingConnection(this, executionContext().executionStrategy());
        }

        @Override
        public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
            return delegate().request(request);
        }

        @Override
        public <T> Publisher<? extends T> transportEventStream(final HttpEventKey<T> eventKey) {
            return delegate().transportEventStream(eventKey);
        }

        @Override
        public HttpConnectionContext connectionContext() {
            return delegate().connectionContext();
        }

        @Override
        public HttpExecutionContext executionContext() {
            return delegate().executionContext();
        }

        @Override
        public StreamingHttpResponseFactory httpResponseFactory() {
            return delegate().httpResponseFactory();
        }

        @Override
        public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            return delegate().newRequest(method, requestTarget);
        }

        @Override
        public void close() throws Exception {
            delegate().close();
        }

        @Override
        public void closeGracefully() throws Exception {
            delegate().closeGracefully();
        }

        @Override
        public String toString() {
            return delegate().toString();
        }
    }
}
