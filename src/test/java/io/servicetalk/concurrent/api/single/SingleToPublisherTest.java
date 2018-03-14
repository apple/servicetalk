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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.Single;
import org.junit.Rule;
import org.junit.Test;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

public class SingleToPublisherTest {

    @Rule
    public MockedSubscriberRule verifier = new MockedSubscriberRule();

    @Test
    public void testSuccessfulFuture() throws Exception {
        verifier.subscribe(Single.success("Hello")).verifySuccess("Hello");
    }

    @Test
    public void testFailedFuture() throws Exception {
        verifier.subscribe(Single.error(DELIBERATE_EXCEPTION)).requestAndVerifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testCancelBeforeRequest() throws Exception {
        verifier.subscribe(Single.success("Hello")).cancel().verifyNoEmissions();
    }

    @Test
    public void testCancelAfterRequest() throws Exception {
        verifier.subscribe(Single.success("Hello")).verifySuccess("Hello").cancel();
    }

    @Test
    public void testInvalidRequestN() {
        verifier.subscribe(Single.success("Hello")).request(-1).verifyFailure(IllegalArgumentException.class);
    }

    @Test
    public void exceptionInTerminalCallsOnError() {
        verifier.subscribe(Single.success("Hello"));
        // The mock behavior must be applied after subscribe, because a new mock is created as part of this process.
        doAnswer(invocation -> {
            throw DELIBERATE_EXCEPTION;
        }).when(verifier.getSubscriber()).onNext(any());
        verifier.request(1).verifyItems("Hello").verifyFailure(DELIBERATE_EXCEPTION);
    }
}
