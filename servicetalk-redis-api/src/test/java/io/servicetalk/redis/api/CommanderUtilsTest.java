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
package io.servicetalk.redis.api;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SingleProcessor;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import static io.servicetalk.redis.api.CommanderUtils.enqueueForExecute;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class CommanderUtilsTest {

    @Test
    public void testCancelCommandFuture() {
        final List<SingleProcessor<?>> singles = new ArrayList<>();
        final Cancellable cancellable = mock(Cancellable.class);
        final Single<String> queued = new Single<String>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super String> subscriber) {
                subscriber.onSubscribe(cancellable);
            }
        };

        Future<Object> future = enqueueForExecute(CommanderUtils.STATE_PENDING, singles, queued);

        future.cancel(true);

        verify(cancellable, never()).cancel();
    }
}
