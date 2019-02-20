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

import io.servicetalk.concurrent.SingleSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static java.util.Objects.requireNonNull;

final class CompletionStageToSingle<T> extends Single<T> implements SingleSource<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompletionStageToSingle.class);
    private final CompletionStage<T> stage;

    CompletionStageToSingle(CompletionStage<T> stage) {
        this.stage = requireNonNull(stage);
    }

    @Override
    protected void handleSubscribe(final Subscriber<? super T> subscriber) {
        if (stage instanceof Future) {
            try {
                subscriber.onSubscribe(() -> ((Future<?>) stage).cancel(true));
            } catch (Throwable t) {
                LOGGER.debug("Ignoring exception from onSubscribe of Subscriber {}.", subscriber, t);
            }
        } else {
            CompletableFuture<T> future = toCompletableFuture();
            if (future != null) {
                try {
                    subscriber.onSubscribe(() -> future.cancel(true));
                } catch (Throwable t) {
                    LOGGER.debug("Ignoring exception from onSubscribe of Subscriber {}.", subscriber, t);
                }
            } else {
                try {
                    subscriber.onSubscribe(IGNORE_CANCEL);
                } catch (Throwable t) {
                    LOGGER.debug("Ignoring exception from onSubscribe of Subscriber {}.", subscriber, t);
                }
            }
        }

        stage.whenComplete((value, cause) -> {
           if (cause != null) {
               try {
                   subscriber.onError(cause);
               } catch (Throwable t) {
                   LOGGER.debug("Ignoring exception from onError of Subscriber {}.", subscriber, t);
               }
           } else {
               try {
                   subscriber.onSuccess(value);
               } catch (Throwable t) {
                   LOGGER.debug("Ignoring exception from onSuccess of Subscriber {}.", subscriber, t);
               }
           }
        });
    }

    @Nullable
    private CompletableFuture<T> toCompletableFuture() {
        try {
            return stage.toCompletableFuture();
        } catch (Throwable cause) {
            return null;
        }
    }

    @Override
    public void subscribe(final Subscriber<? super T> subscriber) {
        subscribeInternal(subscriber);
    }
}
