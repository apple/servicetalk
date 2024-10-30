/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.CancelImmediatelySubscriber;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.slf4j.Logger;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Supplier;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;

final class HttpMessageDiscardWatchdogFilterCleaner {

    private static final ReferenceQueue<Object> REFERENCE_QUEUE = new ReferenceQueue<>();

    // We need something to keep our phantom references from being GC'd prematurely.
    private static final Map<Reference<Object>, Cleanable> LIVE_SET = new ConcurrentHashMap<>();

    static {
        Thread t = new Thread(() -> watchdogQueueRunLoop(), "watchdog-cleaner");
        t.setDaemon(true);
        t.start();
    }

    private static final class Cleanable implements Supplier<PublisherSource.Subscriber<Object>> {

        private static final AtomicIntegerFieldUpdater<Cleanable> UPDATER =
                AtomicIntegerFieldUpdater.newUpdater(Cleanable.class, "value");

        private final Logger logger;
        private final Publisher<?> msgPublisher;
        private volatile int value;

        private Cleanable(Logger logger, Publisher<?> msgPublisher) {
            this.logger = logger;
            this.msgPublisher = msgPublisher;
        }

        @Override
        public PublisherSource.Subscriber<Object> get() {
            signalDisposed();
            return HttpMessageDiscardWatchdogServiceFilter.NoopSubscriber.INSTANCE;
        }

        private boolean signalDisposed() {
            return UPDATER.getAndSet(this, 1) == 0;
        }

        private void dispose() {
            if (signalDisposed()) {
                logger.warn(HttpMessageDiscardWatchdogFilter.WARN_MESSAGE);
                toSource(msgPublisher).subscribe(CancelImmediatelySubscriber.INSTANCE);
            }
        }
    }

    private HttpMessageDiscardWatchdogFilterCleaner() {
        // no instances.
    }

    static StreamingHttpResponse instrument(Logger logger, StreamingHttpResponse response) {
        return response.transformMessageBody(msgPublisher -> {
            Cleanable cleanable = new Cleanable(logger, msgPublisher);
            Publisher<?> result = msgPublisher.beforeSubscriber(cleanable);
            PhantomReference<Object> w = new PhantomReference<>(result, REFERENCE_QUEUE);
            LIVE_SET.put(w, cleanable);
            return result;
        });
    }

    private static void watchdogQueueRunLoop() {
        while (true) {
            try {
                Reference<?> wrapper = REFERENCE_QUEUE.remove();
                Cleanable cleanable = LIVE_SET.remove(wrapper);
                assert cleanable != null;
                cleanable.dispose();
            } catch (InterruptedException ex) {
                Thread.interrupted();
            } catch (Throwable ex) {
                // swallow.
            }
        }
    }
}
