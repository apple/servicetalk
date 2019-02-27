/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

public class ConcatWithHandleSubscribeOrderingTest extends AbstractConcatWithOrderingTest {

    @Override
    protected Completable completable(final int number) {
        return Completable.defer(() -> {
            sb.append(number);
            return Completable.completed();
        });
    }

    @Override
    protected Single<Integer> single(final int number) {
        return Single.defer(() -> {
            sb.append(number);
            return Single.success(0);
        });
    }

    @Override
    protected Publisher<Integer> publisher(final int number) {
        return Publisher.defer(() -> {
            sb.append(number);
            return Publisher.from(0, 1, 2);
        });
    }
}
