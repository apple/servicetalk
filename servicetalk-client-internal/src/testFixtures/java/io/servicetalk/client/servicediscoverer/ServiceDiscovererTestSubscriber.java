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
package io.servicetalk.client.servicediscoverer;

import io.servicetalk.client.api.ServiceDiscovererEvent;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

// TODO(scott): make available outside this package to use in other ServiceDiscoverer tests
public final class ServiceDiscovererTestSubscriber<T> implements Subscriber<ServiceDiscovererEvent<T>> {
    private final CountDownLatch latch;
    private final AtomicReference<Throwable> throwableRef;
    private final long initialRequestN;
    private final Set<T> activeAddresses;
    private int activeCount;
    private int inactiveCount;

    public ServiceDiscovererTestSubscriber(CountDownLatch latch, AtomicReference<Throwable> throwableRef,
                                           long initialRequestN) {
        this.latch = requireNonNull(latch);
        this.throwableRef = requireNonNull(throwableRef);
        this.initialRequestN = initialRequestN;
        activeAddresses = new HashSet<>();
    }

    @Override
    public void onSubscribe(Subscription s) {
        s.request(initialRequestN);
    }

    @Override
    public void onNext(ServiceDiscovererEvent<T> event) {
        if (event.available()) {
            processActiveEvent(event);
        } else {
            processInactiveEvent(event);
        }
        latch.countDown();
    }

    private void processInactiveEvent(ServiceDiscovererEvent<T> event) {
        ++inactiveCount;
        if (!activeAddresses.remove(event.address())) {
            throwableRef.set(new IllegalStateException("address: " + event.address() + " removed but not active"));
            countDownLatchToZero();
        }
    }

    private void processActiveEvent(ServiceDiscovererEvent<T> event) {
        ++activeCount;
        if (!activeAddresses.add(event.address())) {
            throwableRef.set(new IllegalStateException("address: " + event.address() + " is already active"));
            countDownLatchToZero();
        }
    }

    public int getActiveCount() {
        return activeCount;
    }

    public int getInactiveCount() {
        return inactiveCount;
    }

    @Override
    public void onError(Throwable t) {
        throwableRef.set(t);
        countDownLatchToZero();
    }

    @Override
    public void onComplete() {
        throwableRef.set(new IllegalStateException("unexpected onComplete"));
        countDownLatchToZero();
    }

    private void countDownLatchToZero() {
        while (latch.getCount() != 0) {
            latch.countDown();
        }
    }
}
