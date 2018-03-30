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
package io.servicetalk.concurrent.api;

import org.reactivestreams.Subscriber;

import java.util.concurrent.TimeUnit;

/**
 * An {@link Executor} that executes all the submitted tasks via {@link #execute(Runnable)} in the order that they were
 * submitted. {@link #schedule(long, TimeUnit)} uses the provided {@link Executor}.
  */
interface InOrderExecutor extends Executor {

    /**
     * Wraps the passed {@link Subscriber} such that all interactions to and from it are done on this {@link Executor}.
     *
     * @param subscriber {@link Subscriber} to wrap.
     * @param <T> Type of items received by the wrapped and returned {@link Subscriber}.
     * @return New {@link Subscriber} that delegates to the passed {@link Subscriber} but invoked it from this {@link Executor}.
     */
    <T> Subscriber<? super T> wrap(Subscriber<? super T> subscriber);

    /**
     * Wraps the passed {@link Single.Subscriber} such that all interactions to and from it are done on this {@link Executor}.
     *
     * @param subscriber {@link Single.Subscriber} to wrap.
     * @param <T> Type of items received by the wrapped and returned {@link Single.Subscriber}.
     * @return New {@link Single.Subscriber} that delegates to the passed {@link Single.Subscriber} but invoked it from this {@link Executor}.
     */
    <T> Single.Subscriber<? super T> wrap(Single.Subscriber<? super T> subscriber);

    /**
     * Wraps the passed {@link Completable.Subscriber} such that all interactions to and from it are done on this {@link Executor}.
     *
     * @param subscriber {@link Completable.Subscriber} to wrap.
     * @return New {@link Completable.Subscriber} that delegates to the passed {@link Completable.Subscriber} but invoked it from this {@link Executor}.
     */
    Completable.Subscriber wrap(Completable.Subscriber subscriber);
}
