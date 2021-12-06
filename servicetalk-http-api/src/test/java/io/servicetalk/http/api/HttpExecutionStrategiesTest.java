/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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

import org.junit.jupiter.api.Test;

import static io.servicetalk.http.api.HttpExecutionStrategies.customStrategyBuilder;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.difference;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNever;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpExecutionStrategiesTest {

    @Test
    void mergeDefaultStrategy() {
        HttpExecutionStrategy strategy = customStrategyBuilder().offloadAll().build();
        HttpExecutionStrategy merged = strategy.merge(defaultStrategy());
        assertThat("merge returned defaultStrategy.", merged, sameInstance(strategy));
        merged = defaultStrategy().merge(strategy);
        assertThat("merge returned defaultStrategy.", merged, sameInstance(strategy));
    }

    @Test
    void defaultShouldOffloadAll() {
        HttpExecutionStrategy strategy = defaultStrategy();
        assertThat("send not offloaded by default.", strategy.isSendOffloaded(), is(true));
        assertThat("receive meta not offloaded by default.", strategy.isMetadataReceiveOffloaded(), is(true));
        assertThat("receive data not offloaded by default.", strategy.isDataReceiveOffloaded(), is(true));
    }

    @Test
    void diffLeftAndRightEqual() {
        HttpExecutionStrategy strat = customStrategyBuilder().offloadSend().build();
        HttpExecutionStrategy result = difference(strat, strat);
        assertThat("Unexpected diff.", result, is(nullValue()));
    }

    @Test
    void diffRightNoOffload() {
        HttpExecutionStrategy strat1 = customStrategyBuilder().offloadReceiveData().build();
        HttpExecutionStrategy strat2 = offloadNever();
        HttpExecutionStrategy result = difference(strat1, strat2);
        assertThat("Unexpected diff.", result, is(nullValue()));
    }

    @Test
    void diffLeftNoOffload() {
        HttpExecutionStrategy strat1 = offloadNever();
        HttpExecutionStrategy strat2 = customStrategyBuilder().offloadReceiveData().build();
        HttpExecutionStrategy result = difference(strat1, strat2);
        assertThat("Unexpected diff.", result, is(sameInstance(strat2)));
    }

    @Test
    void diffLeftAndRightDiffOffloadNoExecutor() {
        HttpExecutionStrategy strat1 = customStrategyBuilder().offloadReceiveData().build();
        HttpExecutionStrategy strat2 = customStrategyBuilder().offloadSend().build();
        HttpExecutionStrategy result = difference(strat1, strat2);
        assertThat("Unexpected diff.", result, is(notNullValue()));
        assertThat("Unexpected offload (send) in diff.", result.isSendOffloaded(), is(true));
        assertThat("Unexpected offload (meta receive) in diff.", result.isMetadataReceiveOffloaded(), is(false));
        assertThat("Unexpected offload (data receive) in diff.", result.isDataReceiveOffloaded(), is(false));
    }

    @Test
    void diffLeftNoOffloadAndRightAllOffloads() {
        HttpExecutionStrategy strat1 = customStrategyBuilder().offloadNone().build();
        HttpExecutionStrategy strat2 = customStrategyBuilder().offloadAll().build();
        HttpExecutionStrategy result = difference(strat1, strat2);
        assertThat("Unexpected diff.", result, is(notNullValue()));
        assertThat("Unexpected offload (send) in diff.", result.isSendOffloaded(), is(true));
        assertThat("Unexpected offload (meta receive) in diff.", result.isMetadataReceiveOffloaded(), is(true));
        assertThat("Unexpected offload (data receive) in diff.", result.isDataReceiveOffloaded(), is(true));
    }

    @Test
    void diffEqualButDifferentInstances() {
        HttpExecutionStrategy strat1 = customStrategyBuilder().offloadAll().build();
        HttpExecutionStrategy strat2 = newMockStrategyOffloadAll();
        HttpExecutionStrategy result = difference(strat1, strat2);
        assertThat("Unexpected diff.", result, is(nullValue()));
    }

    @Test
    void missingRightNoOffload() {
        HttpExecutionStrategy strat1 = customStrategyBuilder().offloadReceiveData().build();
        HttpExecutionStrategy strat2 = offloadNever();
        HttpExecutionStrategy result = strat1.missing(strat2);
        assertThat("Unexpected diff.", result, is(sameInstance(HttpExecutionStrategies.offloadNone())));
    }

    @Test
    void missingLeftNoOffload() {
        HttpExecutionStrategy strat1 = offloadNever();
        HttpExecutionStrategy strat2 = customStrategyBuilder().offloadReceiveData().build();
        HttpExecutionStrategy result = strat1.missing(strat2);
        assertThat("Unexpected diff.", result, is(sameInstance(strat2)));
    }

    @Test
    void missingLeftAndRightDiffOffload() {
        HttpExecutionStrategy strat1 = customStrategyBuilder().offloadReceiveData().build();
        HttpExecutionStrategy strat2 = customStrategyBuilder().offloadSend().build();
        HttpExecutionStrategy result = strat1.missing(strat2);
        assertThat("Unexpected diff.", result, is(notNullValue()));
        assertThat("Unexpected offload (send) in diff.", result.isSendOffloaded(), is(true));
        assertThat("Unexpected offload (meta receive) in diff.", result.isMetadataReceiveOffloaded(), is(false));
        assertThat("Unexpected offload (data receive) in diff.", result.isDataReceiveOffloaded(), is(false));
    }

    @Test
    void missingLeftNoOffloadAndRightAllOffloads() {
        HttpExecutionStrategy strat1 = customStrategyBuilder().offloadNone().build();
        HttpExecutionStrategy strat2 = customStrategyBuilder().offloadAll().build();
        HttpExecutionStrategy result = strat1.missing(strat2);
        assertThat("Unexpected diff.", result, is(notNullValue()));
        assertThat("Unexpected offload (send) in diff.", result.isSendOffloaded(), is(true));
        assertThat("Unexpected offload (meta receive) in diff.", result.isMetadataReceiveOffloaded(), is(true));
        assertThat("Unexpected offload (data receive) in diff.", result.isDataReceiveOffloaded(), is(true));
    }

    @Test
    void missingEqualButDifferentInstances() {
        HttpExecutionStrategy strat1 = customStrategyBuilder().offloadAll().build();
        HttpExecutionStrategy strat2 = newMockStrategyOffloadAll();
        HttpExecutionStrategy result = difference(strat1, strat2);
        assertThat("Unexpected diff.", result, is(nullValue()));
    }

    @Test
    void missingLeftAndRightEqual() {
        HttpExecutionStrategy strat = customStrategyBuilder().offloadSend().build();
        HttpExecutionStrategy result = difference(strat, strat);
        assertThat("Unexpected diff.", result, is(nullValue()));
    }

    private HttpExecutionStrategy newMockStrategyOffloadAll() {
        HttpExecutionStrategy mock = mock(HttpExecutionStrategy.class);
        when(mock.isSendOffloaded()).thenReturn(true);
        when(mock.isDataReceiveOffloaded()).thenReturn(true);
        when(mock.isMetadataReceiveOffloaded()).thenReturn(true);
        return mock;
    }
}
