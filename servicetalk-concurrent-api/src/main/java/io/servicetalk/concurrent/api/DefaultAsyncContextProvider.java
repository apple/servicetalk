/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.context.api.ContextMapHolder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.lang.ThreadLocal.withInitial;

class DefaultAsyncContextProvider implements AsyncContextProvider {

    private static final ThreadLocal<ContextMap> CONTEXT_THREAD_LOCAL =
            withInitial(DefaultAsyncContextProvider::newContextMap);

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAsyncContextProvider.class);
    static final AsyncContextProvider INSTANCE = new DefaultAsyncContextProvider();
    private static final boolean NO_DEBUG_LOGGING = isNoDebugLogging();

    protected DefaultAsyncContextProvider() {
    }

    @Override
    public final ContextMap context() {
        final Thread t = Thread.currentThread();
        if (t instanceof ContextMapHolder) {
            final ContextMapHolder contextMapHolder = (ContextMapHolder) t;
            ContextMap map = contextMapHolder.context();
            if (map == null) {
                map = newContextMap();
                contextMapHolder.context(map);
            }
            return map;
        } else {
            return CONTEXT_THREAD_LOCAL.get();
        }
    }

    @Override
    public final void setContextMap(ContextMap contextMap) {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof ContextMapHolder) {
            final ContextMapHolder asyncContextMapHolder = (ContextMapHolder) currentThread;
            asyncContextMapHolder.context(contextMap);
        } else {
            CONTEXT_THREAD_LOCAL.set(contextMap);
        }
    }

    @Override
    public final Scope attachContextMap(ContextMap contextMap) {
        return doAttachContextMap(contextMap);
    }

    @Override
    public final CapturedContext captureContext() {
        return captureContext(context());
    }

    @Override
    public CapturedContext captureContext(ContextMap contextMap) {
        return convertToCapturedContext(contextMap);
    }

    @Override
    public CapturedContext captureContextCopy() {
        return convertToCapturedContext(context().copy());
    }

    private CapturedContext convertToCapturedContext(ContextMap contextMap) {
        return contextMap instanceof CapturedContext ?
                (CapturedContext) contextMap : new CapturedContextImpl(contextMap);
    }

    @Override
    public final CompletableSource.Subscriber wrapCancellable(final CompletableSource.Subscriber subscriber,
                                                              final CapturedContext context) {
        if (subscriber instanceof ContextPreservingCompletableSubscriber) {
            final ContextPreservingCompletableSubscriber s = (ContextPreservingCompletableSubscriber) subscriber;
            // Cancellation happens going the other way as the subscriber wrapping: the cancellable is coming
            // in via the outermost call and thus the outer wrapper will be the one to set the effective context.
            return s.cancellableCapturedContext == context ? subscriber :
                    new ContextPreservingCompletableSubscriber(s.subscriber, context, s.subscriberCapturedContext);
        }
        return new ContextPreservingCompletableSubscriber(subscriber, context, null);
    }

    @Override
    public final CompletableSource.Subscriber wrapCompletableSubscriber(final CompletableSource.Subscriber subscriber,
                                                                        final CapturedContext context) {
        if (subscriber instanceof ContextPreservingCompletableSubscriber) {
            final ContextPreservingCompletableSubscriber s = (ContextPreservingCompletableSubscriber) subscriber;
            return s.subscriberCapturedContext != null ? subscriber :
                    new ContextPreservingCompletableSubscriber(s.subscriber, s.cancellableCapturedContext, context);
        }
        return new ContextPreservingCompletableSubscriber(subscriber, null, context);
    }

    @Override
    public final CompletableSource.Subscriber wrapCompletableSubscriberAndCancellable(
            final CompletableSource.Subscriber subscriber, final CapturedContext context) {
        if (subscriber instanceof ContextPreservingCompletableSubscriber) {
            final ContextPreservingCompletableSubscriber s = (ContextPreservingCompletableSubscriber) subscriber;
            // Cancellation happens going the other way as the subscriber wrapping: the cancellable is coming
            // in via the outermost call and thus the outer wrapper will be the one to set the effective context.
            if (s.cancellableCapturedContext == context && s.subscriberCapturedContext != null) {
                // Subscriber already wrapped.
                return subscriber;
            }
            CapturedContext subscriberContext = s.subscriberCapturedContext == null ?
                    context : s.subscriberCapturedContext;
            return new ContextPreservingCompletableSubscriber(s.subscriber, context, subscriberContext);
        }
        return new ContextPreservingCompletableSubscriber(subscriber, context, context);
    }

    @Override
    public final <T> SingleSource.Subscriber<T> wrapCancellable(final SingleSource.Subscriber<T> subscriber,
                                                                final CapturedContext context) {
        if (subscriber instanceof ContextPreservingSingleSubscriber) {
            final ContextPreservingSingleSubscriber<T> s = (ContextPreservingSingleSubscriber<T>) subscriber;
            // The most outer wrapper gets the first shot at wrapping the cancellable, so if the previous context
            // doesn't match the new context, we need to replace it with the new context who now has the first shot.
            return s.cancellableCapturedContext == context ? subscriber :
                    new ContextPreservingSingleSubscriber<>(s.subscriber, context, s.subscriberCapturedContext);
        }
        return new ContextPreservingSingleSubscriber<>(subscriber, context, null);
    }

    @Override
    public final <T> SingleSource.Subscriber<T> wrapSingleSubscriber(final SingleSource.Subscriber<T> subscriber,
                                                                     final CapturedContext context) {
        if (subscriber instanceof ContextPreservingSingleSubscriber) {
            final ContextPreservingSingleSubscriber<T> s = (ContextPreservingSingleSubscriber<T>) subscriber;
            return s.subscriberCapturedContext != null ? subscriber :
                    new ContextPreservingSingleSubscriber<>(s.subscriber, s.cancellableCapturedContext, context);
        }
        return new ContextPreservingSingleSubscriber<>(subscriber, null, context);
    }

    @Override
    public final <T> SingleSource.Subscriber<T> wrapSingleSubscriberAndCancellable(
            final SingleSource.Subscriber<T> subscriber, final CapturedContext context) {
        if (subscriber instanceof ContextPreservingSingleSubscriber) {
            final ContextPreservingSingleSubscriber<T> s = (ContextPreservingSingleSubscriber<T>) subscriber;
            if (s.cancellableCapturedContext == context && s.subscriberCapturedContext != null) {
                // Subscriber already wrapped.
                return subscriber;
            }
            CapturedContext subscriberContext = s.subscriberCapturedContext == null ?
                    context : s.subscriberCapturedContext;
            return new ContextPreservingSingleSubscriber<>(s.subscriber, context, subscriberContext);
        }
        return new ContextPreservingSingleSubscriber<>(subscriber, context, context);
    }

    @Override
    public final <T> PublisherSource.Subscriber<T> wrapSubscription(
            final PublisherSource.Subscriber<T> subscriber, final CapturedContext context) {
        if (subscriber instanceof ContextPreservingSubscriber) {
            final ContextPreservingSubscriber<T> s = (ContextPreservingSubscriber<T>) subscriber;
            return s.subscriptionCapturedContext == context ? subscriber :
                    new ContextPreservingSubscriber<>(s.subscriber, context, s.subscriberCapturedContext);
        }
        return new ContextPreservingSubscriber<>(subscriber, context, null);
    }

    @Override
    public final <T> PublisherSource.Subscriber<T> wrapPublisherSubscriber(
            final PublisherSource.Subscriber<T> subscriber, final CapturedContext context) {
        if (subscriber instanceof ContextPreservingSubscriber) {
            final ContextPreservingSubscriber<T> s = (ContextPreservingSubscriber<T>) subscriber;
            return s.subscriberCapturedContext != null ? subscriber :
                    new ContextPreservingSubscriber<>(s.subscriber, s.subscriptionCapturedContext, context);
        }
        return new ContextPreservingSubscriber<>(subscriber, null, context);
    }

    @Override
    public final <T> PublisherSource.Subscriber<T> wrapPublisherSubscriberAndSubscription(
            final PublisherSource.Subscriber<T> subscriber, final CapturedContext context) {
        if (subscriber instanceof ContextPreservingSubscriber) {
            final ContextPreservingSubscriber<T> s = (ContextPreservingSubscriber<T>) subscriber;
            if (s.subscriptionCapturedContext == context && s.subscriberCapturedContext != null) {
                // Subscriber already wrapped.
                return subscriber;
            }
            CapturedContext subscriberContext = s.subscriberCapturedContext == null ?
                    context : s.subscriberCapturedContext;
            return new ContextPreservingSubscriber<>(s.subscriber, context, subscriberContext);
        }
        return new ContextPreservingSubscriber<>(subscriber, context, context);
    }

    @Override
    public final java.util.concurrent.Executor wrapJdkExecutor(final java.util.concurrent.Executor executor) {
        return ContextPreservingExecutor.of(executor);
    }

    @Override
    public final ExecutorService wrapJdkExecutorService(final ExecutorService executor) {
        return ContextPreservingExecutorService.of(executor);
    }

    @Override
    public final io.servicetalk.concurrent.api.Executor wrapExecutor(
            final io.servicetalk.concurrent.api.Executor executor) {
        return ContextPreservingStExecutor.of(executor);
    }

    @Override
    public final ScheduledExecutorService wrapJdkScheduledExecutorService(final ScheduledExecutorService executor) {
        return ContextPreservingScheduledExecutorService.of(executor);
    }

    @Override
    public final <T> CompletableFuture<T> wrapCompletableFuture(final CompletableFuture<T> future,
                                                                final CapturedContext context) {
        return ContextPreservingCompletableFuture.newContextPreservingFuture(future, context);
    }

    @Override
    public final Runnable wrapRunnable(final Runnable runnable, final CapturedContext context) {
        return new ContextPreservingRunnable(runnable, context);
    }

    @Override
    public final <V> Callable<V> wrapCallable(final Callable<V> callable, final CapturedContext context) {
        return new ContextPreservingCallable<>(callable, context);
    }

    @Override
    public final <T> Consumer<T> wrapConsumer(final Consumer<T> consumer, final CapturedContext context) {
        return new ContextPreservingConsumer<>(consumer, context);
    }

    @Override
    public final <T, U> Function<T, U> wrapFunction(final Function<T, U> func, final CapturedContext context) {
        return new ContextPreservingFunction<>(func, context);
    }

    @Override
    public final <T, U> BiConsumer<T, U> wrapBiConsumer(
            final BiConsumer<T, U> consumer, final CapturedContext context) {
        return new ContextPreservingBiConsumer<>(consumer, context);
    }

    @Override
    public final <T, U, V> BiFunction<T, U, V> wrapBiFunction(
            final BiFunction<T, U, V> func, final CapturedContext context) {
        return new ContextPreservingBiFunction<>(func, context);
    }

    private static final class CapturedContextImpl implements CapturedContext {

        private final ContextMap contextMap;

        CapturedContextImpl(ContextMap contextMap) {
            this.contextMap = contextMap;
        }

        @Override
        public ContextMap captured() {
            return contextMap;
        }

        @Override
        public Scope attachContext() {
            return doAttachContextMap(contextMap);
        }
    }

    private static final class DetachScope implements Scope {
        private final ContextMap expectedContext;
        private final ContextMap toRestore;

        DetachScope(ContextMap expectedContext, ContextMap toRestore) {
            this.expectedContext = expectedContext;
            this.toRestore = toRestore;
        }

        @Override
        public void close() {
            ContextMap current = exchangeContext(toRestore);
            if (current != expectedContext && !NO_DEBUG_LOGGING) {
                LOGGER.debug("Current context didn't match the expected context. current: {}, expected: {}",
                        current, expectedContext, new Throwable("stack trace"));
            }
        }
    }

    private static Scope doAttachContextMap(ContextMap contextMap) {
        ContextMap prev = exchangeContext(contextMap);
        return NO_DEBUG_LOGGING && prev instanceof Scope ? (Scope) prev : new DetachScope(contextMap, prev);
    }

    private static ContextMap exchangeContext(ContextMap contextMap) {
        final Thread currentThread = Thread.currentThread();
        ContextMap result;
        if (currentThread instanceof ContextMapHolder) {
            final ContextMapHolder asyncContextMapHolder = (ContextMapHolder) currentThread;
            result = asyncContextMapHolder.context();
            if (result == null) {
                result = newContextMap();
            }
            asyncContextMapHolder.context(contextMap);
        } else {
            result = CONTEXT_THREAD_LOCAL.get();
            CONTEXT_THREAD_LOCAL.set(contextMap);
        }
        return result;
    }

    private static ContextMap newContextMap() {
        return new CopyOnWriteContextMap();
    }

    @SuppressWarnings("PMD.SystemPrintln")
    private static boolean isNoDebugLogging() {
        try {
            return !LOGGER.isDebugEnabled();
        } catch (Exception ex) {
            // Logger isn't initialized, so we have to send it to a console.
            System.err.println("Could not evaluate logging level, considering debug level is disabled by default. " +
                    "Cause:" + System.lineSeparator() + ex);
            ex.printStackTrace(System.err);
            return true;
        }
    }
}
