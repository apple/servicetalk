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

import java.util.Arrays;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;

public class IterableMergeCompletableDelayErrorTest {
    @Rule
    public final MergeCompletableTest.CompletableHolder collectionHolder = new MergeCompletableTest.CompletableHolder() {
        @Override
        protected Completable createCompletable(Completable[] completables) {
            return new IterableMergeCompletable(true, completables[0], Arrays.asList(completables).subList(1, completables.length));
        }
    };

    @Rule
    public final MergeCompletableTest.CompletableHolder iterableHolder = new MergeCompletableTest.CompletableHolder() {
        @Override
        protected Completable createCompletable(Completable[] completables) {
            return new IterableMergeCompletable(true, completables[0], () -> Arrays.asList(completables).subList(1, completables.length).iterator());
        }
    };

    @Test
    public void testCollectionCompletion() throws Exception {
        collectionHolder.init(2).listen().completeAll().verifyCompletion();
    }

    @Test
    public void testCollectionCompletionFew() throws Exception {
        collectionHolder.init(2).listen().complete(1, 2).verifyNoEmissions().complete(0).verifyCompletion();
    }

    @Test
    public void testCollectionFailFirstEvent() throws Exception {
        collectionHolder.init(2).listen().fail(1).verifyNoEmissions().complete(0, 2).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testCollectionFailLastEvent() {
        collectionHolder.init(2).listen().complete(0, 2).verifyNoEmissions().fail(1).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testCollectionFailMiddleEvent() {
        collectionHolder.init(2).listen().complete(0).verifyNoEmissions().fail(1).verifyNoEmissions().complete(2).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testCollectionMergeWithOne() throws Exception {
        collectionHolder.init(1).listen().completeAll().verifyCompletion();
    }

    @Test
    public void testIterableCompletion() throws Exception {
        iterableHolder.init(2).listen().completeAll().verifyCompletion();
    }

    @Test
    public void testIterableCompletionFew() throws Exception {
        iterableHolder.init(2).listen().complete(1, 2).verifyNoEmissions().complete(0).verifyCompletion();
    }

    @Test
    public void testIterableFailFirstEvent() throws Exception {
        iterableHolder.init(2).listen().fail(1).verifyNoEmissions().complete(0, 2).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testIterableFailLastEvent() {
        iterableHolder.init(2).listen().complete(0, 2).verifyNoEmissions().fail(1).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testIterableFailMiddleEvent() {
        iterableHolder.init(2).listen().complete(0).verifyNoEmissions().fail(1).verifyNoEmissions().complete(2).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testIterableMergeWithOne() throws Exception {
        iterableHolder.init(1).listen().completeAll().verifyCompletion();
    }
}
