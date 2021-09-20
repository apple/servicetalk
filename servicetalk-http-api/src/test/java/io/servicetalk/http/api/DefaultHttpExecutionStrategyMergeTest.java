/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.api.Executor;

import org.junit.jupiter.api.Test;

import static io.servicetalk.http.api.HttpExecutionStrategies.customStrategyBuilder;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultHttpExecutionStrategyMergeTest {

    private final Executor executor = mock(Executor.class);
    private final Executor otherExecutor = mock(Executor.class);

    @Test
    void equalStrategy() {
        HttpExecutionStrategy strategy = customStrategyBuilder().offloadSend().build();
        HttpExecutionStrategy other = customStrategyBuilder().offloadSend().build();
        HttpExecutionStrategy merged = strategy.merge(other);
        assertThat("Unexpected merge result.", merged, is(sameInstance(strategy)));
    }

    @Test
    void differentStrategy() {
        HttpExecutionStrategy strategy = customStrategyBuilder().offloadSend().build();
        HttpExecutionStrategy other = customStrategyBuilder().offloadReceiveData().build();
        HttpExecutionStrategy merged = strategy.merge(other);
        assertThat("Unexpected merge result.", merged, is(not(sameInstance(strategy))));
        assertThat("Unexpected merge result.", merged, is(not(sameInstance(other))));
        assertThat("Unexpected merge result for send offload.", merged.isSendOffloaded(), is(true));
        assertThat("Unexpected merge result for data receive offload.", merged.isDataReceiveOffloaded(), is(true));
        assertThat("Unexpected merge result for meta receive offload", merged.isMetadataReceiveOffloaded(), is(false));
    }

    @Test
    void sameStrategy() {
        HttpExecutionStrategy strategy = customStrategyBuilder().offloadSend().build();
        HttpExecutionStrategy merged = strategy.merge(strategy);
        assertThat("Unexpected merge result.", merged, is(sameInstance(strategy)));
    }

    @Test
    void mergeWithNoOffloads() {
        HttpExecutionStrategy strategy = customStrategyBuilder().offloadSend().build();
        HttpExecutionStrategy merged = strategy.merge(noOffloadsStrategy());
        assertThat("Unexpected merge result.", merged, is(sameInstance(noOffloadsStrategy())));
    }

    @Test
    void nonDefaultStrategyButEqual() {
        HttpExecutionStrategy strategy = customStrategyBuilder().offloadSend().build();
        HttpExecutionStrategy other = mock(HttpExecutionStrategy.class);
        when(other.isSendOffloaded()).thenReturn(true);
        HttpExecutionStrategy merged = strategy.merge(other);
        assertThat("Unexpected merge result.", merged, is(sameInstance(strategy)));
    }

    @Test
    void nonDefaultStrategyAndDifferent() {
        HttpExecutionStrategy strategy = customStrategyBuilder().offloadSend().build();
        HttpExecutionStrategy other = mock(HttpExecutionStrategy.class);
        when(other.isDataReceiveOffloaded()).thenReturn(true);
        HttpExecutionStrategy merged = strategy.merge(other);
        assertThat("Unexpected merge result.", merged, is(not(sameInstance(strategy))));
        assertThat("Unexpected merge result.", merged, is(not(sameInstance(other))));
        assertThat("Unexpected merge result for send offload.", merged.isSendOffloaded(), is(true));
        assertThat("Unexpected merge result for data receive offload.", merged.isDataReceiveOffloaded(), is(true));
        assertThat("Unexpected merge result for meta receive offload", merged.isMetadataReceiveOffloaded(), is(false));
    }

    @Test
    void nonDefaultStrategyOverrideExecutorAndDifferent() {
        HttpExecutionStrategy strategy = customStrategyBuilder().offloadSend().build();
        HttpExecutionStrategy other = mock(HttpExecutionStrategy.class);
        when(other.isDataReceiveOffloaded()).thenReturn(true);
        HttpExecutionStrategy merged = strategy.merge(other);
        assertThat("Unexpected merge result.", merged, is(not(sameInstance(strategy))));
        assertThat("Unexpected merge result.", merged, is(not(sameInstance(other))));
        assertThat("Unexpected merge result for send offload.", merged.isSendOffloaded(), is(true));
        assertThat("Unexpected merge result for data receive offload.", merged.isDataReceiveOffloaded(), is(true));
        assertThat("Unexpected merge result for meta receive offload", merged.isMetadataReceiveOffloaded(), is(false));
    }
}
