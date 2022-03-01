/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Processors;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class LoadBalancerReadySubscriberTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void terminalPersistsFailure(boolean onError) {
        PublisherSource.Processor<Object, Object> processor = Processors.newPublisherProcessor();
        LoadBalancerReadySubscriber subscriber = new LoadBalancerReadySubscriber();
        processor.subscribe(subscriber);

        if (onError) {
            processor.onError(DELIBERATE_EXCEPTION);
            for (int i = 0; i < 5; ++i) {
                assertThat(assertThrows(ExecutionException.class,
                                () -> subscriber.onHostsAvailable().toFuture().get()).getCause(),
                        is(DELIBERATE_EXCEPTION));
            }
        } else {
            processor.onComplete();
            for (int i = 0; i < 5; ++i) {
                assertThat(assertThrows(ExecutionException.class,
                                () -> subscriber.onHostsAvailable().toFuture().get()).getCause(),
                        instanceOf(IllegalStateException.class));
            }
        }
    }
}
