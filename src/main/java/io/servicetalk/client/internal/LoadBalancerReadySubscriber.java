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
package io.servicetalk.client.internal;

import io.servicetalk.client.api.LoadBalancerReadyEvent;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompletableProcessor;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import javax.annotation.Nullable;

/**
 * Designed to listen for {@link LoadBalancerReadyEvent}s and provide notification when a {@link LoadBalancerReadyEvent}
 * returns {@code true} from {@link LoadBalancerReadyEvent#isReady()}.
 */
public final class LoadBalancerReadySubscriber implements Subscriber<Object> {
    @Nullable
    private volatile CompletableProcessor onHostsAvailable = new CompletableProcessor();

    /**
     * Get {@link Completable} that will complete when a {@link LoadBalancerReadyEvent} returns {@code true}
     * from {@link LoadBalancerReadyEvent#isReady()}.
     * @return A {@link Completable} that will complete when a {@link LoadBalancerReadyEvent} returns {@code true}
     * from {@link LoadBalancerReadyEvent#isReady()}, or {@code null} if this event has already been seen and a
     * a {@link LoadBalancerReadyEvent} that returns {@code true} has not been seend.
     */
    @Nullable
    public Completable onHostsAvailable() {
        return onHostsAvailable;
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
                CompletableProcessor onHostsAvailable = LoadBalancerReadySubscriber.this.onHostsAvailable;
                if (onHostsAvailable != null) {
                    LoadBalancerReadySubscriber.this.onHostsAvailable = null;
                    onHostsAvailable.onComplete();
                }
            } else if (LoadBalancerReadySubscriber.this.onHostsAvailable == null) {
                LoadBalancerReadySubscriber.this.onHostsAvailable = new CompletableProcessor();
            }
        }
    }

    @Override
    public void onError(final Throwable t) {
        CompletableProcessor onHostsAvailable = LoadBalancerReadySubscriber.this.onHostsAvailable;
        if (onHostsAvailable != null) {
            LoadBalancerReadySubscriber.this.onHostsAvailable = null;
            onHostsAvailable.onError(t);
        }
    }

    @Override
    public void onComplete() {
        CompletableProcessor onHostsAvailable = LoadBalancerReadySubscriber.this.onHostsAvailable;
        if (onHostsAvailable != null) {
            LoadBalancerReadySubscriber.this.onHostsAvailable = null;
            // Let the load balancer or retry strategy fail any pending requests.
            onHostsAvailable.onComplete();
        }
    }
}
