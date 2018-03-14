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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.internal.SequentialCancellable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SimpleCompletableSubscriber extends SequentialCancellable implements Completable.Subscriber {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleCompletableSubscriber.class);

    @Override
    public void onSubscribe(Cancellable cancellable) {
        setNextCancellable(cancellable);
    }

    @Override
    public void onComplete() {
    }

    @Override
    public void onError(Throwable t) {
        LOGGER.debug("Received exception from the source.", t);
    }
}
