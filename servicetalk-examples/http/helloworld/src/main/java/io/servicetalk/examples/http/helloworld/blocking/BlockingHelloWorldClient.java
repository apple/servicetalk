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
package io.servicetalk.examples.http.helloworld.blocking;

import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;

import java.util.Collection;

public final class BlockingHelloWorldClient {

    static final Executor publishExecutor = Executors.newCachedThreadExecutor(new DefaultThreadFactory("publish"));
    static final Executor subscribeExecutor = Executors.newCachedThreadExecutor(new DefaultThreadFactory("subscribe"));

    public static void main(String[] args) throws Exception {
        Collection<Integer> simple = Publisher.range(1, 3)
                .map(element -> element)
                .publishOn(publishExecutor)
                .map(element -> element)
                .subscribeOn(subscribeExecutor)
                .toFuture()
                .get();
        Collection<?> result = Publisher.range(1, 3)
        .map(element -> {
            System.out.println("\nPublish starts on " + Thread.currentThread() + " Received : " + element);
            return element;
        })
        .whenOnSubscribe(subscription -> {
            System.out.println("\nonSubscribe starts on " + Thread.currentThread());
        })
        .publishOn(publishExecutor)
        .map(element -> {
            System.out.println("\nPublish offloaded to " + Thread.currentThread() + " Received : " + element);
            return element;
        })
        .whenRequest(request -> {
            System.out.println("\nrequest(" + request + ") offloaded to " + Thread.currentThread());
        })
        .liftSync(subscriber -> {
            System.out.println("\nSubscribe offloaded to " + Thread.currentThread());
            return subscriber;
        })
        .subscribeOn(subscribeExecutor)
        .liftSync(subscriber -> {
            System.out.println("\nSubscribe begins on " + Thread.currentThread());
            return subscriber;
        })
        .whenOnSubscribe(subscription -> {
            System.out.println("\nonSubscribe offloaded to " + Thread.currentThread());
        })
        .whenRequest(request -> {
            System.out.println("\nrequest(" + request + ") starts on " + Thread.currentThread());
        })
        .whenFinally(new TerminalSignalConsumer() {
            @Override
            public void onComplete() {
                        System.out.println("\ncomplete on " + Thread.currentThread());
                    }

            @Override
            public void onError(final Throwable throwable) {
                System.out.println("\nerror (" + throwable + ") on " + Thread.currentThread());
            }

            @Override
                public void cancel() {
                        System.out.println("\ncancel on " + Thread.currentThread());
                    }
        })
        .toFuture()
        .get();

        assert simple.equals(result);
    }
}
