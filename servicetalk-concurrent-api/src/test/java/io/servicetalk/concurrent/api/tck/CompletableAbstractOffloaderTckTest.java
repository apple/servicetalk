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
package io.servicetalk.concurrent.api.tck;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;

abstract class CompletableAbstractOffloaderTckTest extends AbstractCompletableOperatorTckTest {
    private Executor executor;

    @Override
    @BeforeMethod
    public void setUp() throws Exception {
        executor = newCachedThreadExecutor();
        super.setUp();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        executor.closeAsync().toVoidFuture().get();
    }

    @Override
    protected Completable composeCompletable(final Completable completable) {
        return applyOffload(completable, executor);
    }

    abstract Completable applyOffload(Completable original, Executor executor);
}
