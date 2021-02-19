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
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;

import java.util.function.Consumer;

/**
 * A contract to offload <a
 * href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#glossary">signals</a> to and
 * from any asynchronous source.
 *
 * <h2>Caution</h2>
 * A {@link SignalOffloader} instance <strong>MUST</strong> only be used for a single asynchronous execution chain at
 * any given time. Reusing it across different execution chains concurrently may result in deadlock.
 * Concurrent invocation of any {@link SignalOffloader} methods may result in deadlock.
 */
public interface SignalOffloader {

    /**
     * Decorates the passed {@link Subscriber} such that all method calls to it will be offloaded.
     *
     * <h2>Caution</h2>
     * This method MUST not be called concurrently with itself or other offload methods here on the same
     * {@link SignalOffloader} instance.
     *
     * @param subscriber {@link Subscriber} for which the signals are to be offloaded.
     * @param <T> Type of items received by the passed and returned {@link Subscriber}.
     * @return New {@link Subscriber} that will offload signals to the passed {@link Subscriber}.
     */
    <T> Subscriber<? super T> offloadSubscriber(Subscriber<? super T> subscriber);

    /**
     * Decorates the passed {@link SingleSource.Subscriber} such that all method calls to it will be offloaded.
     * <h2>Caution</h2>
     * This method MUST not be called concurrently with itself or other offload methods here on the same
     * {@link SignalOffloader} instance.
     *
     * @param subscriber {@link SingleSource.Subscriber} for which the signals are to be offloaded.
     * @param <T> Type of items received by the passed and returned {@link SingleSource.Subscriber}.
     * @return New {@link SingleSource.Subscriber} that will offload signals to the passed
     * {@link SingleSource.Subscriber}.
     */
    <T> SingleSource.Subscriber<? super T> offloadSubscriber(SingleSource.Subscriber<? super T> subscriber);

    /**
     * Decorates the passed {@link CompletableSource.Subscriber} such that all method calls to it will be offloaded.
     * <h2>Caution</h2>
     * This method MUST not be called concurrently with itself or other offload methods here on the same
     * {@link SignalOffloader} instance.
     *
     * @param subscriber {@link CompletableSource.Subscriber} for which the signals are to be offloaded.
     * @return New {@link CompletableSource.Subscriber} that will offload signals to the passed
     * {@link CompletableSource.Subscriber}.
     */
    CompletableSource.Subscriber offloadSubscriber(CompletableSource.Subscriber subscriber);

    /**
     * Decorates the passed {@link Subscriber} such that all method calls to its {@link Subscription} will be offloaded.
     * <em>None of the {@link Subscriber} methods will be offloaded.</em>
     * <h2>Caution</h2>
     * This method MUST not be called concurrently with itself or other offload methods here on the same
     * {@link SignalOffloader} instance.
     *
     * @param subscriber {@link Subscriber} for which the signals are to be offloaded.
     * @param <T> Type of items received by the passed and returned {@link Subscriber}.
     * @return New {@link Subscriber} that will offload signals to the passed {@link Subscriber}.
     */
    <T> Subscriber<? super T> offloadSubscription(Subscriber<? super T> subscriber);

    /**
     * Decorates the passed {@link SingleSource.Subscriber} such that all method calls to its {@link Cancellable} will
     * be offloaded.
     * <em>None of the {@link SingleSource.Subscriber} methods will be offloaded.</em>
     * <h2>Caution</h2>
     * This method MUST not be called concurrently with itself or other offload methods here on the same
     * {@link SignalOffloader} instance.
     *
     * @param subscriber {@link SingleSource.Subscriber} for which the signals are to be offloaded.
     * @param <T> Type of items received by the passed and returned {@link SingleSource.Subscriber}.
     * @return New {@link SingleSource.Subscriber} that will offload signals to the passed
     * {@link SingleSource.Subscriber}.
     */
    <T> SingleSource.Subscriber<? super T> offloadCancellable(SingleSource.Subscriber<? super T> subscriber);

    /**
     * Decorates the passed {@link CompletableSource.Subscriber} such that all method calls to its {@link Cancellable}
     * will be offloaded.
     * <em>None of the {@link CompletableSource.Subscriber} methods will be offloaded.</em>
     * <h2>Caution</h2>LoadBalancerReadyHttpClientTest
     * This method MUST not be called concurrently with itself or other offload methods here on the same
     * {@link SignalOffloader} instance.
     *
     * @param subscriber {@link CompletableSource.Subscriber} for which the signals are to be offloaded.
     * @return New {@link CompletableSource.Subscriber} that will offload signals to the passed
     * {@link CompletableSource.Subscriber}.
     */
    CompletableSource.Subscriber offloadCancellable(CompletableSource.Subscriber subscriber);

    /**
     * Offloads subscribe call for the passed {@link Subscriber}.
     *
     * <h2>Offloading Failures</h2>
     * Implementations are expected to handle failure to offload, e.g. If a thread pool is used to offload and it
     * rejects task submissions. In such situations, it is expected that the passed {@link Subscriber} will be
     * correctly terminated.
     * <h2>Caution</h2>
     * This method MUST not be called concurrently with itself or other offload methods here on the same
     * {@link SignalOffloader} instance.
     *
     * @param subscriber {@link Subscriber} for which subscribe call has to be offloaded.
     * @param handleSubscribe {@link Consumer} to handle the offloaded subscribe call.
     * @param <T> Type of signal.
     */
    <T> void offloadSubscribe(Subscriber<? super T> subscriber, Consumer<Subscriber<? super T>> handleSubscribe);

    /**
     * Offloads subscribe call for the passed {@link Subscriber}.
     *
     * <h2>Offloading Failures</h2>
     * Implementations are expected to handle failure to offload, e.g. If a thread pool is used to offload and it
     * rejects task submissions. In such situations, it is expected that the passed {@link Subscriber} will be
     * correctly terminated.
     * <h2>Caution</h2>
     * This method MUST not be called concurrently with itself or other offload methods here on the same
     * {@link SignalOffloader} instance.
     *
     * @param subscriber {@link SingleSource.Subscriber} for which subscribe call has to be offloaded.
     * @param handleSubscribe {@link Consumer} to handle the offloaded subscribe call.
     * @param <T> Type of signal.
     */
    <T> void offloadSubscribe(SingleSource.Subscriber<? super T> subscriber,
                              Consumer<SingleSource.Subscriber<? super T>> handleSubscribe);

    /**
     * Offloads the subscribe call for the passed {@link Subscriber}.
     *
     * <h2>Offloading Failures</h2>
     * Implementations are expected to handle failure to offload, e.g. If a thread pool is used to offload and it
     * rejects task submissions. In such situations, it is expected that the passed {@link Subscriber} will be
     * correctly terminated.
     * <h2>Caution</h2>
     * This method MUST not be called concurrently with itself or other offload methods here on the same
     * {@link SignalOffloader} instance.
     *
     * @param subscriber {@link Subscriber} for which for which subscribe call has to be offloaded.
     * @param handleSubscribe {@link Consumer} to handle the offloaded subscribe call.
     */
    void offloadSubscribe(CompletableSource.Subscriber subscriber,
                          Consumer<CompletableSource.Subscriber> handleSubscribe);

    /**
     * Offloads the consumption of the passed {@code signal} by the passed {@link Consumer}.
     *
     * <h2>Caution</h2>
     * This method MUST not be called concurrently with itself or other offload methods here on the same
     * {@link SignalOffloader} instance.
     *
     * @param signal {@code signal} to send to the {@link Consumer}.
     * @param signalConsumer {@link Consumer} of the signal.
     * @param <T> Type of signal.
     */
    <T> void offloadSignal(T signal, Consumer<T> signalConsumer);
}
