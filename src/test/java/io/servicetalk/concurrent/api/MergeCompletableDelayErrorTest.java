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
package io.servicetalk.concurrent.api;

import org.junit.Rule;
import org.junit.Test;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.copyOfRange;

public class MergeCompletableDelayErrorTest {
    @Rule
    public final MergeCompletableTest.CompletableHolder holder = new MergeCompletableTest.CompletableHolder() {
        @Override
        protected Completable createCompletable(Completable[] completables) {
            return new MergeCompletable(true, completables[0], copyOfRange(completables, 1, completables.length));
        }
    };

    @Test
    public void testCompletion() throws Exception {
        holder.init(2).listen().completeAll().verifyCompletion();
    }

    @Test
    public void testCompletionFew() throws Exception {
        holder.init(2).listen().complete(1, 2).verifyNoEmissions().complete(0).verifyCompletion();
    }

    @Test
    public void testFailFirstEvent() throws Exception {
        holder.init(2).listen().fail(1).verifyNoEmissions().complete(0, 2).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testFailLastEvent() {
        holder.init(2).listen().complete(0, 2).verifyNoEmissions().fail(1).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testFailMiddleEvent() {
        holder.init(2).listen().complete(0).verifyNoEmissions().fail(1).verifyNoEmissions().complete(2).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testMergeWithOne() throws Exception {
        holder.init(1).listen().completeAll().verifyCompletion();
    }
}
