/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.http.virtualthreads;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.http.netty.HttpServers;

import java.util.concurrent.ThreadFactory;

import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static java.util.concurrent.Executors.newThreadPerTaskExecutor;

/**
 * A "Hello World" server that runs request handling on virtual threads.
 * <p>
 * Virtual threads are enabled by supplying an offloading {@link Executor} that starts a fresh virtual thread per
 * task. The {@code IoExecutor} (Netty's event loops) is deliberately left at its platform-thread default: only
 * offloaded application work runs on virtual threads.
 */
public final class VirtualThreadServer {

    public static void main(String... args) throws Exception {
        // Name the virtual threads (rather than the default "VirtualThread[#N]") so they are identifiable in thread
        // dumps, logs, and profilers - important when debugging a real workload.
        ThreadFactory factory = Thread.ofVirtual().name("servicetalk-vt-", 0).factory();
        Executor executor = Executors.from(newThreadPerTaskExecutor(factory));

        HttpServers.forPort(8080)
                .executor(executor)
                .listenBlockingAndAwait((ctx, request, responseFactory) ->
                        // This runs on a virtual thread. Blocking here (a downstream client call, a JDBC query,
                        // ...) parks the virtual thread without holding onto a platform thread, so a small number
                        // of event-loop threads can carry a large number of concurrent, mostly-waiting requests.
                        responseFactory.ok().payloadBody(
                                "Hello World! (from " + Thread.currentThread() + ')', textSerializerUtf8()))
                .awaitShutdown();
    }
}
