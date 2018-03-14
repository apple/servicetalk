/**
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

import org.junit.Test;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;

public class WriteSingleSubscriberTest extends AbstractWriteTest {

    @Test
    public void testSuccess() {
        WriteSingleSubscriber listener = new WriteSingleSubscriber(channel, completableSubscriber);
        listener.onSuccess("Hello");
        channel.flushOutbound();
        verify(completableSubscriber).onComplete();
        assertThat("Message not written.", channel.readOutbound(), is("Hello"));
    }

    @Test(expected = NullPointerException.class)
    public void testNullResult() {
        WriteSingleSubscriber listener = new WriteSingleSubscriber(channel, completableSubscriber);
        listener.onSuccess(null);
    }

    @Test
    public void testError() {
        WriteSingleSubscriber listener = new WriteSingleSubscriber(channel, completableSubscriber);
        listener.onError(DELIBERATE_EXCEPTION);
        verify(completableSubscriber).onError(DELIBERATE_EXCEPTION);
    }
}
