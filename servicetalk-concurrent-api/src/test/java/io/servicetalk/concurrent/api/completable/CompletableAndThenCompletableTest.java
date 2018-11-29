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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.api.MockedCompletableListenerRule;
import io.servicetalk.concurrent.api.TestCompletable;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;

public class CompletableAndThenCompletableTest {

    @Rule
    public final MockedCompletableListenerRule listener = new MockedCompletableListenerRule();

    private TestCompletable source;
    private TestCompletable next;

    @Before
    public void setUp() throws Exception {
        source = new TestCompletable();
        next = new TestCompletable();
    }

    @Test
    public void testSourceSuccessNextSuccess() {
        listener.listen(source.andThen(next));
        source.onComplete();
        listener.verifyNoEmissions();
        next.onComplete();
        listener.verifyCompletion();
    }

    @Test
    public void testSourceSuccessNextError() {
        listener.listen(source.andThen(next));
        source.onComplete();
        listener.verifyNoEmissions();
        next.onError(DELIBERATE_EXCEPTION);
        listener.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testSourceError() {
        listener.listen(source.andThen(next));
        source.onError(DELIBERATE_EXCEPTION);
        listener.verifyFailure(DELIBERATE_EXCEPTION);
        next.verifyListenNotCalled();
    }

    @Test
    public void testCancelSource() {
        listener.listen(source.andThen(next));
        listener.verifyNoEmissions();
        listener.cancel();
        source.verifyCancelled();
        next.verifyListenNotCalled();
    }

    @Test
    public void testCancelNext() {
        listener.listen(source.andThen(next));
        source.onComplete();
        listener.verifyNoEmissions();
        listener.cancel();
        source.verifyNotCancelled();
        next.verifyCancelled();
    }
}
