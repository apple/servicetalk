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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ConnectionContext;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Single.error;
import static java.util.Objects.requireNonNull;

public class BlockingHttpClientTest extends AbstractBlockingHttpRequesterTest {
    @SuppressWarnings("unchecked")
    @Override
    protected <I, O, T extends HttpRequester<I, O> & TestHttpRequester> T newAsyncRequester(
            final ConnectionContext ctx, final Function<HttpRequest<I>, Single<HttpResponse<O>>> doRequest) {
        return (T) new TestHttpClient<I, O>(ctx.getExecutor()) {
            @Override
            public Single<HttpResponse<O>> request(final HttpRequest<I> request) {
                return doRequest.apply(request);
            }

            @Override
            public Single<? extends ReservedHttpConnection<I, O>> reserveConnection(final HttpRequest<I> request) {
                return error(new UnsupportedOperationException());
            }

            @Override
            public Single<? extends UpgradableHttpResponse<I, O>> upgradeConnection(final HttpRequest<I> request) {
                return error(new UnsupportedOperationException());
            }
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <I, O, T extends BlockingHttpRequester<I, O> & TestHttpRequester> T newBlockingRequester(
            final ConnectionContext ctx, final Function<BlockingHttpRequest<I>, BlockingHttpResponse<O>> doRequest) {
        return (T) new TestBlockingHttpClient<I, O>(ctx.getExecutor()) {
            @Override
            public BlockingHttpResponse<O> request(final BlockingHttpRequest<I> request) {
                return doRequest.apply(request);
            }

            @Override
            public BlockingReservedHttpConnection<I, O> reserveConnection(final BlockingHttpRequest<I> request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public BlockingUpgradableHttpResponse<I, O> upgradeConnection(final BlockingHttpRequest<I> request) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private abstract static class TestHttpClient<I, O> extends HttpClient<I, O> implements TestHttpRequester {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final CompletableProcessor onClose = new CompletableProcessor();
        private final Executor executor;

        TestHttpClient(Executor executor) {
            this.executor = requireNonNull(executor);
        }

        @Override
        public Executor getExecutor() {
            return executor;
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

    private abstract static class TestBlockingHttpClient<I, O> extends BlockingHttpClient<I, O>
                                                               implements TestHttpRequester {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final Executor executor;

        TestBlockingHttpClient(Executor executor) {
            this.executor = requireNonNull(executor);
        }

        @Override
        public void close() {
            closed.set(true);
        }

        @Override
        public final boolean isClosed() {
            return closed.get();
        }

        @Override
        public Executor getExecutor() {
            return executor;
        }
    }
}
