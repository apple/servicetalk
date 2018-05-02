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

import io.servicetalk.concurrent.api.BlockingIterable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ConnectionContext;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static java.util.Objects.requireNonNull;

public class BlockingHttpConnectionTest extends AbstractBlockingHttpRequesterTest {

    @SuppressWarnings("unchecked")
    @Override
    protected <I, O, T extends HttpRequester<I, O> & TestHttpRequester> T newAsyncRequester(final ConnectionContext ctx,
                                               final Function<HttpRequest<I>, Single<HttpResponse<O>>> doRequest) {
        return (T) new TestHttpConnection<I, O>(ctx) {
            @Override
            public Single<HttpResponse<O>> request(final HttpRequest<I> request) {
                return doRequest.apply(request);
            }
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <I, O, T extends BlockingHttpRequester<I, O> & TestHttpRequester> T newBlockingRequester(
            final ConnectionContext ctx, final Function<BlockingHttpRequest<I>, BlockingHttpResponse<O>> doRequest) {
        return (T) new TestBlockingHttpConnection<I, O>(ctx) {
            @Override
            public BlockingHttpResponse<O> request(final BlockingHttpRequest<I> request) {
                return doRequest.apply(request);
            }
        };
    }

    private abstract static class TestHttpConnection<I, O> extends HttpConnection<I, O> implements TestHttpRequester {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final CompletableProcessor onClose = new CompletableProcessor();
        private final ConnectionContext connectionContext;

        TestHttpConnection(ConnectionContext connectionContext) {
            this.connectionContext = requireNonNull(connectionContext);
        }

        @Override
        public final ConnectionContext getConnectionContext() {
            return connectionContext;
        }

        @Override
        public final <T> Publisher<T> getSettingStream(final SettingKey<T> settingKey) {
            return Publisher.error(new IllegalStateException("unsupported"), immediate());
        }

        @Override
        public final Completable onClose() {
            return onClose;
        }

        @Override
        public final Completable closeAsync() {
            return new Completable() {
                @Override
                protected void handleSubscribe(final Subscriber subscriber) {
                    if (closed.compareAndSet(false, true)) {
                        onClose.onComplete();
                    }
                    onClose.subscribe(subscriber);
                }
            };
        }

        @Override
        public final boolean isClosed() {
            return closed.get();
        }
    }

    private abstract static class TestBlockingHttpConnection<I, O> extends BlockingHttpConnection<I, O>
            implements TestHttpRequester {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final ConnectionContext connectionContext;

        TestBlockingHttpConnection(ConnectionContext connectionContext) {
            this.connectionContext = requireNonNull(connectionContext);
        }

        @Override
        public ConnectionContext getConnectionContext() {
            return connectionContext;
        }

        @Override
        public <T> BlockingIterable<T> getSettingIterable(final HttpConnection.SettingKey<T> settingKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            closed.set(true);
        }

        @Override
        public final boolean isClosed() {
            return closed.get();
        }
    }
}
