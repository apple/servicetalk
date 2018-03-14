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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.api.MockedCompletableListenerRule;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.concurrent.api.TestSingle;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.api.Single.success;

public final class SingleFlatmapCompletableTest {

    @Rule public final MockedCompletableListenerRule listener = new MockedCompletableListenerRule();
    private TestSingle<String> single;
    private TestCompletable completable;

    @Before
    public void setUp() throws Exception {
        single = new TestSingle<>();
        completable = new TestCompletable();
    }

    @Test
    public void testSuccess() throws Exception {
        listener.listen(success(1).flatmapCompletable(s -> completed()));
        listener.verifyCompletion();
    }

    @Test
    public void testFirstEmitsError() throws Exception {
        listener.listen(Single.error(DELIBERATE_EXCEPTION).flatmapCompletable(s -> completable));
        listener.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testSecondEmitsError() throws Exception {
        listener.listen(success(1).flatmapCompletable(s -> error(DELIBERATE_EXCEPTION)));
        listener.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testCancelBeforeFirst() throws Exception {
        listener.listen(single.flatmapCompletable(s -> completable));
        listener.cancel();
        single.verifyCancelled();
    }

    @Test
    public void testCancelBeforeSecond() throws Exception {
        listener.listen(single.flatmapCompletable(s -> completable));
        single.onSuccess("Hello");
        listener.cancel();
        single.verifyNotCancelled();
        completable.verifyCancelled();
    }

    @Test
    public void exceptionInTerminalCallsOnError() {
        listener.listen(single.flatmapCompletable(s -> {
            throw DELIBERATE_EXCEPTION;
        }));
        single.onSuccess("Hello");
        listener.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void nullInTerminalCallsOnError() {
        listener.listen(single.flatmapCompletable(s -> null));
        single.onSuccess("Hello");
        listener.verifyFailure(NullPointerException.class);
    }
}
