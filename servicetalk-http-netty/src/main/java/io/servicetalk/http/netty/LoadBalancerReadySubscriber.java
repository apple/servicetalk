/*
 * Copyright © 2018-2019, 2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.LoadBalancerReadyEvent;
import io.servicetalk.concurrent.CompletableSource.Processor;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.internal.DelayedCancellable;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;

/**
 * Designed to listen for {@link LoadBalancerReadyEvent}s and provide notification when a {@link LoadBalancerReadyEvent}
 * returns {@code true} from {@link LoadBalancerReadyEvent#isReady()}.
 */
final class LoadBalancerReadySubscriber extends DelayedCancellable implements Subscriber<Object> {
    @Nullable
    private volatile Processor onHostsAvailable = newCompletableProcessor();

    /**
     * Get {@link Completable} that will complete when a {@link LoadBalancerReadyEvent} returns {@code true}
     * from {@link LoadBalancerReadyEvent#isReady()}.
     * @return A {@link Completable} that will complete when a {@link LoadBalancerReadyEvent} returns {@code true}
     * from {@link LoadBalancerReadyEvent#isReady()}.
     */
    public Completable onHostsAvailable() {
        Processor onHostsAvailable = this.onHostsAvailable;
        return onHostsAvailable == null ? completed() : fromSource(onHostsAvailable);
    }

    @Override
    public void onSubscribe(final Subscription s) {
        s.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(final Object o) {
        if (o instanceof LoadBalancerReadyEvent) {
            LoadBalancerReadyEvent event = (LoadBalancerReadyEvent) o;
            if (event.isReady()) {
                Processor onHostsAvailable = this.onHostsAvailable;
                if (onHostsAvailable != null) {
                    this.onHostsAvailable = null;
                    onHostsAvailable.onComplete();
                }
            } else if (onHostsAvailable == null) {
                onHostsAvailable = newCompletableProcessor();
            }
        }
    }

    @Override
    public void onError(final Throwable t) {
        Processor onHostsAvailable = this.onHostsAvailable;
        if (onHostsAvailable != null) {
            onHostsAvailable.onError(t);
        }
    }

    @Override
    public void onComplete() {
        Processor onHostsAvailable = this.onHostsAvailable;
        if (onHostsAvailable != null) {
            // Let the load balancer or retry strategy fail any pending requests.
            onHostsAvailable.onComplete();
        }
    }
}
