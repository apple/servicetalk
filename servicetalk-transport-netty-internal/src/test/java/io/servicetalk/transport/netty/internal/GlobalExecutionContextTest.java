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

import io.servicetalk.transport.api.ExecutionContext;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static io.servicetalk.transport.netty.internal.GlobalExecutionContext.globalExecutionContext;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.toNettyIoExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;

class GlobalExecutionContextTest {

    @Test
    void testGetGlobalExecutionContext() throws InterruptedException {
        ExecutionContext<?> gec = globalExecutionContext();
        CountDownLatch scheduleLatch = new CountDownLatch(2);
        gec.executor().schedule(scheduleLatch::countDown, 5, MILLISECONDS);
        NettyIoExecutor ioExecutor = toNettyIoExecutor(gec.ioExecutor());
        assertThat("global ioExecutor does not support IoThread", ioExecutor.isIoThreadSupported());
        ioExecutor.schedule(scheduleLatch::countDown, 5, MILLISECONDS);
        scheduleLatch.await();
    }
}
