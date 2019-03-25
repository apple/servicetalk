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
package io.servicetalk.concurrent.reactivestreams.tck;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;

abstract class SingleAbstractOffloaderTckTest extends AbstractSingleOperatorTckTest<Integer> {
    private Executor executor;

    @Override
    @BeforeMethod
    public void setUp() throws Exception {
        executor = newCachedThreadExecutor();
        super.setUp();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        executor.closeAsync().toFuture().get();
    }

    @Override
    protected Single<Integer> composeSingle(final Single<Integer> single) {
        return applyOffload(single, executor);
    }

    abstract Single<Integer> applyOffload(Single<Integer> original, Executor executor);
}
