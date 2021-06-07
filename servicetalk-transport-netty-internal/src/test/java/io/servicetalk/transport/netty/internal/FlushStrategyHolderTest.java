/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;

class FlushStrategyHolderTest {

    private final FlushStrategyHolder holder;

    FlushStrategyHolderTest() {
        holder = new FlushStrategyHolder(mock(FlushStrategy.class));
    }

    @Test
    void updateWhenOriginal() {
        FlushStrategy prev = holder.currentStrategy();
        FlushStrategy next = mock(FlushStrategy.class);
        Cancellable revert =
                holder.updateFlushStrategy((current, isCurrentOriginal) -> isCurrentOriginal ? next : current);
        assertThat("Unexpected strategy post update.", holder.currentStrategy(), equalTo(next));
        revert.cancel();
        assertThat("Unexpected strategy post revert.", holder.currentStrategy(), equalTo(prev));
    }

    @Test
    void revertNoOpOnMismatch() {
        FlushStrategy prev = holder.currentStrategy();
        FlushStrategy next1 = mock(FlushStrategy.class);
        Cancellable revert1 =
                holder.updateFlushStrategy((current, isCurrentOriginal) -> isCurrentOriginal ? next1 : current);
        assertThat("Unexpected strategy post update.", holder.currentStrategy(), equalTo(next1));

        FlushStrategy next2 = mock(FlushStrategy.class);
        Cancellable revert2 = holder.updateFlushStrategy((current, isCurrentOriginal) -> next2);

        revert1.cancel();
        // Cancel should be a noop since we have already updated
        assertThat("Unexpected strategy post revert.", holder.currentStrategy(), equalTo(next2));

        revert2.cancel();
        // Cancel should revert to original
        assertThat("Unexpected strategy post revert.", holder.currentStrategy(), equalTo(prev));
    }
}
