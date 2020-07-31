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

import io.servicetalk.concurrent.api.Publisher;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import org.mockito.InOrder;

import static io.servicetalk.transport.netty.internal.Flush.composeFlushes;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

abstract class AbstractFlushTest {

    Channel channel;
    private InOrder verifier;

    Publisher<String> setup(Publisher<String> source, FlushStrategy strategy) {
        channel = mock(Channel.class);
        EventLoop eventLoop = mock(EventLoop.class);
        when(eventLoop.inEventLoop()).thenReturn(true);
        when(channel.eventLoop()).thenReturn(eventLoop);
        Publisher<String> flushedStream = composeFlushes(channel, source, strategy, null)
                .beforeOnNext(s -> channel.write(s));
        verifier = inOrder(channel);
        return flushedStream;
    }

    void verifyWrite(String... items) {
        for (String item : items) {
            verifier.verify(channel).write(item);
        }
    }

    void verifyWriteAndFlushAfter(String... items) {
        verifyWrite(items);
        verifyFlush();
        verify(channel).eventLoop();
    }

    void verifyFlush() {
        verifier.verify(channel).flush();
    }
}
