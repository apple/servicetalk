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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.api.Executor;

import org.junit.Test;
import org.mockito.Mockito;

import static io.servicetalk.http.api.HttpExecutionStrategies.customStrategyBuilder;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;

public class HttpExecutionStrategiesTest {

    @Test
    public void defaultShouldOffloadAll() {
        HttpExecutionStrategy strategy = defaultStrategy();
        assertThat("send not offloaded by default.", strategy.isSendOffloaded(), is(true));
        assertThat("receive meta not offloaded by default.", strategy.isMetadataReceiveOffloaded(), is(true));
        assertThat("receive data not offloaded by default.", strategy.isDataReceiveOffloaded(), is(true));
    }

    @Test
    public void noOffloadsWithExecutor() {
        Executor executor = Mockito.mock(Executor.class);
        HttpExecutionStrategy strategy = customStrategyBuilder().executor(executor).build();
        assertThat("Unexpected executor.", strategy.executor(), sameInstance(executor));
    }
}
