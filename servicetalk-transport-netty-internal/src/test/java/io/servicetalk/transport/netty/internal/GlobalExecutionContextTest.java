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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.transport.api.ExecutionContext;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.CountDownLatch;

import static io.servicetalk.transport.netty.internal.GlobalExecutionContext.globalExecutionContext;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.toNettyIoExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;

public class GlobalExecutionContextTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Test
    public void testGetGlobalExecutionContext() throws InterruptedException {
        ExecutionContext gec = globalExecutionContext();
        CountDownLatch scheduleLatch = new CountDownLatch(2);
        gec.getExecutor().schedule(scheduleLatch::countDown, 1, SECONDS);
        toNettyIoExecutor(gec.getIoExecutor()).asExecutor().schedule(scheduleLatch::countDown, 1, SECONDS);
        scheduleLatch.await();
    }
}
