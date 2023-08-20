/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.reactivestreams.tck;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherOperator;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.internal.FlowControlUtils;

import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.defer;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;

@Test
public class PublisherSwitchMapTckTest extends AbstractPublisherOperatorTckTest<Integer> {
    @Override
    protected Publisher<Integer> composePublisher(Publisher<Integer> publisher, int elements) {
        return defer(() -> {
            final SingleUpstreamDemandOperator<Integer> demandOperator = new SingleUpstreamDemandOperator<>();
            return publisher.liftAsync(demandOperator)
                    .switchMap(i -> from(i).afterOnNext(x -> demandOperator.subscriberRef.get().decrementDemand()));
        });
    }

    static final class SingleUpstreamDemandOperator<T> implements PublisherOperator<T, T> {
        final AtomicReference<SingleUpstreamDemandSubscriber<T>> subscriberRef = new AtomicReference<>();
        @Override
        public PublisherSource.Subscriber<? super T> apply(final PublisherSource.Subscriber<? super T> subscriber) {
            SingleUpstreamDemandSubscriber<T> sub = new SingleUpstreamDemandSubscriber<>(subscriber);
            if (subscriberRef.compareAndSet(null, sub)) {
                return sub;
            } else {
                return new PublisherSource.Subscriber<T>() {
                    @Override
                    public void onSubscribe(final Subscription subscription) {
                        deliverErrorFromSource(subscriber,
                                new DuplicateSubscribeException(subscriberRef.get(), subscriber));
                    }

                    @Override
                    public void onNext(@Nullable final T t) {
                    }

                    @Override
                    public void onError(final Throwable t) {
                    }

                    @Override
                    public void onComplete() {
                    }
                };
            }
        }

        static final class SingleUpstreamDemandSubscriber<T> implements PublisherSource.Subscriber<T> {
            private final AtomicLong demand = new AtomicLong();
            private final PublisherSource.Subscriber<? super T> subscriber;
            @Nullable
            private Subscription subscription;

            SingleUpstreamDemandSubscriber(final PublisherSource.Subscriber<? super T> subscriber) {
                this.subscriber = subscriber;
            }

            @Override
            public void onSubscribe(final Subscription s) {
                this.subscription = s;
                subscriber.onSubscribe(new Subscription() {
                    @Override
                    public void request(final long n) {
                        if (n <= 0) {
                            subscription.request(n);
                        } else if (demand.getAndAccumulate(n, FlowControlUtils::addWithOverflowProtection) == 0) {
                            subscription.request(1);
                        }
                    }

                    @Override
                    public void cancel() {
                        subscription.cancel();
                    }
                });
            }

            @Override
            public void onNext(@Nullable final T t) {
                subscriber.onNext(t);
            }

            @Override
            public void onError(final Throwable t) {
                subscriber.onError(t);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }

            void decrementDemand() {
                if (demand.decrementAndGet() > 0) {
                    assert subscription != null;
                    subscription.request(1);
                }
            }
        }
    }
}
