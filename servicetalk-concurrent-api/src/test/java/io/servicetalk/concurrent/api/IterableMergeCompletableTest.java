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
package io.servicetalk.concurrent.api;

import org.junit.Rule;
import org.junit.Test;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.asList;

public class IterableMergeCompletableTest {
    @Rule
    public final MergeCompletableTest.CompletableHolder collectionHolder =
            new MergeCompletableTest.CompletableHolder() {
        @Override
        protected Completable createCompletable(Completable[] completables) {
            return new IterableMergeCompletable(false, completables[0],
                    asList(completables).subList(1, completables.length), immediate());
        }
    };

    @Rule
    public final MergeCompletableTest.CompletableHolder iterableHolder = new MergeCompletableTest.CompletableHolder() {
        @Override
        protected Completable createCompletable(Completable[] completables) {
            return new IterableMergeCompletable(false, completables[0],
                    () -> asList(completables).subList(1, completables.length).iterator(), immediate());
        }
    };

    @Test
    public void testCollectionCompletion() {
        collectionHolder.init(2).listen().completeAll().verifyCompletion();
    }

    @Test
    public void testCollectionCompletionFew() {
        collectionHolder.init(2).listen().complete(1, 2).verifyNoEmissions().complete(0).verifyCompletion();
    }

    @Test
    public void testCollectionFail() {
        collectionHolder.init(2).listen().fail(1).verifyFailure(DELIBERATE_EXCEPTION).verifyCancelled(0, 2);
    }

    @Test
    public void testCollectionMergeWithOne() {
        collectionHolder.init(1).listen().completeAll().verifyCompletion();
    }

    @Test
    public void testIterableCompletion() {
        iterableHolder.init(2).listen().completeAll().verifyCompletion();
    }

    @Test
    public void testIterableCompletionFew() {
        iterableHolder.init(2).listen().complete(1, 2).verifyNoEmissions().complete(0).verifyCompletion();
    }

    @Test
    public void testIterableFail() {
        iterableHolder.init(2).listen().fail(1).verifyFailure(DELIBERATE_EXCEPTION).verifyCancelled(0, 2);
    }

    @Test
    public void testIterableMergeWithOne() {
        iterableHolder.init(1).listen().completeAll().verifyCompletion();
    }
}
