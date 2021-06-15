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

import io.servicetalk.concurrent.Cancellable;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NettyChannelListenableAsyncCloseableTest {

    @Mock
    Channel channel;
    @Mock
    private ChannelFuture channelCloseFuture;

    NettyChannelListenableAsyncCloseable fixture;

    @BeforeEach
    public void setUp() {
        fixture = new NettyChannelListenableAsyncCloseable(channel, immediate());
        when(channel.closeFuture()).thenReturn(channelCloseFuture);
    }

    @Test
    void cancellingOnCloseShouldNotCancelFuture() {
        fixture.onClose().afterOnSubscribe(Cancellable::cancel).subscribe();
        verify(channelCloseFuture, never()).cancel(anyBoolean());
    }
}
