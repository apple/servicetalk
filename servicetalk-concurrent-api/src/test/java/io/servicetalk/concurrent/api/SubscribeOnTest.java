/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubscribeOnTest {

    private static final String THREAD_NAME_PREFIX = "test-executor";

    @RegisterExtension
    static final ExecutorExtension<Executor> EXECUTOR = ExecutorExtension.withCachedExecutor(THREAD_NAME_PREFIX)
            .setClassLevel(true);

    @Test
    void subscribeOnSingle() throws Exception {
        AtomicReference<String> offloadedThreadName = new AtomicReference<>();
        int value = Single.fromCallable(() -> {
            offloadedThreadName.set(Thread.currentThread().getName());
            return 1;
        }).subscribeOn(EXECUTOR.executor()).toFuture().get();

        assertThat(value, is(1));
        assertThat(offloadedThreadName.get(), startsWith(THREAD_NAME_PREFIX));
        assertThat(offloadedThreadName.get(), is(not(equalTo(Thread.currentThread().getName()))));
    }

    @ParameterizedTest(name = "{displayName} [{index}] shouldOffload={0}")
    @ValueSource(booleans = {false, true})
    void throwOnSubscribeSingleSubscribeOn(boolean shouldOffload) {
        ExecutionException ee = assertThrows(ExecutionException.class, () -> new ThrowOnSubscribeSingle()
                .subscribeOn(EXECUTOR.executor(), () -> shouldOffload).toFuture().get());
        assertThat(ee.getCause(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void throwOnSubscribeSinglePublishOn() {
        ExecutionException ee = assertThrows(ExecutionException.class, () -> new ThrowOnSubscribeSingle()
                .publishOn(EXECUTOR.executor()).toFuture().get());
        assertThat(ee.getCause(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void subscribeOnCompletable() throws Exception {
        AtomicReference<String> offloadedThreadName = new AtomicReference<>();
        Completable.fromRunnable(() -> offloadedThreadName.set(Thread.currentThread().getName()))
                .subscribeOn(EXECUTOR.executor()).toFuture().get();

        assertThat(offloadedThreadName.get(), startsWith(THREAD_NAME_PREFIX));
        assertThat(offloadedThreadName.get(), is(not(equalTo(Thread.currentThread().getName()))));
    }

    @ParameterizedTest(name = "{displayName} [{index}] shouldOffload={0}")
    @ValueSource(booleans = {false, true})
    void throwOnSubscribeCompletableSubscribeOn(boolean shouldOffload) {
        ExecutionException ee = assertThrows(ExecutionException.class, () -> new ThrowOnSubscribeCompletable()
                .subscribeOn(EXECUTOR.executor(), () -> shouldOffload).toFuture().get());
        assertThat(ee.getCause(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void throwOnSubscribeCompletablePublishOn() {
        ExecutionException ee = assertThrows(ExecutionException.class, () -> new ThrowOnSubscribeCompletable()
                .publishOn(EXECUTOR.executor()).toFuture().get());
        assertThat(ee.getCause(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void subscribeOnPublisher() throws Exception {
        AtomicReference<String> offloadedThreadName = new AtomicReference<>();
        Collection<Object> result = Publisher.defer(() -> {
            offloadedThreadName.set(Thread.currentThread().getName());
            return Publisher.empty();
        }).subscribeOn(EXECUTOR.executor()).toFuture().get();

        assertThat(result, is(empty()));
        assertThat(offloadedThreadName.get(), startsWith(THREAD_NAME_PREFIX));
        assertThat(offloadedThreadName.get(), is(not(equalTo(Thread.currentThread().getName()))));
    }

    @ParameterizedTest(name = "{displayName} [{index}] shouldOffload={0}")
    @ValueSource(booleans = {false, true})
    void throwOnSubscribePublisherSubscribeOn(boolean shouldOffload) {
        ExecutionException ee = assertThrows(ExecutionException.class, () -> new ThrowOnSubscribePublisher()
                .subscribeOn(EXECUTOR.executor(), () -> shouldOffload).toFuture().get());
        assertThat(ee.getCause(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void throwOnSubscribePublisherPublishOn() {
        ExecutionException ee = assertThrows(ExecutionException.class, () -> new ThrowOnSubscribePublisher()
                .publishOn(EXECUTOR.executor()).toFuture().get());
        assertThat(ee.getCause(), is(DELIBERATE_EXCEPTION));
    }

    private static final class ThrowOnSubscribeSingle extends AbstractSynchronousSingle<Integer> {

        @Override
        void doSubscribe(Subscriber<? super Integer> subscriber) {
            throw DELIBERATE_EXCEPTION;
        }
    }

    private static final class ThrowOnSubscribeCompletable extends AbstractSynchronousCompletable {

        @Override
        void doSubscribe(Subscriber subscriber) {
            throw DELIBERATE_EXCEPTION;
        }
    }

    private static final class ThrowOnSubscribePublisher extends AbstractSynchronousPublisher<Integer> {

        @Override
        void doSubscribe(Subscriber<? super Integer> subscriber) {
            throw DELIBERATE_EXCEPTION;
        }
    }
}
