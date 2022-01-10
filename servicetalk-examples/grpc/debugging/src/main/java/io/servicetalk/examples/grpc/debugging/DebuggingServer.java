/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.grpc.debugging;

import io.servicetalk.grpc.netty.GrpcServers;
import io.servicetalk.http.netty.HttpProtocolConfigs;
import io.servicetalk.logging.api.LogLevel;

import io.grpc.examples.debugging.Greeter.BlockingGreeterService;
import io.grpc.examples.debugging.HelloReply;

import java.util.function.BooleanSupplier;

import static io.servicetalk.logging.api.LogLevel.TRACE;

/**
 * The blocking "Hello World" example with debugging features enabled. Five debugging features are demonstrated:
 * <ol>
 *     <li>Disabling {@link io.servicetalk.concurrent.api.AsyncContext}</li>
 *     <li>Disabling {@link io.servicetalk.http.api.HttpExecutionStrategy offloading}</li>
 *     <li>Enabling {@link io.servicetalk.http.api.SingleAddressHttpClientBuilder#enableWireLogging(String, LogLevel, BooleanSupplier) HTTP wire logging}</li>
 *     <li>Enabling {@link io.servicetalk.http.netty.H2ProtocolConfigBuilder#enableFrameLogging(String, LogLevel, BooleanSupplier) HTTP/2 frame logging}</li>
 *     <li>Enabling additional logger verbosity in the {@code log4j2.xml} configuration file</li>
 * </ol>
 * <p>The wire and frame logging features require that you configure a logger with an appropriate log level. For this
 * example {@code log4j2.xml} is used by both the client and server and configures the
 * ({@code servicetalk-examples-wire-logger} logger at {@link io.servicetalk.logging.api.LogLevel#TRACE TRACE} level.
 *
 * <p>When configured correctly the output should be similar to the following:
 * <pre>
 * 2022-01-10 10:34:34,345                           main [DEBUG] AsyncContext                   - Enabled.
 * 2022-01-10 10:34:34,646                           main [DEBUG] GlobalExecutionContext         - Initialized GlobalExecutionContext
 * 2022-01-10 10:34:34,658                           main [DEBUG] GrpcRouter                     - route strategy for path=/helloworld.Greeter/SayHello : ctx=DEFAULT_HTTP_EXECUTION_STRATEGY route=null → using=OFFLOAD_NEVER_STRATEGY
 * 2022-01-10 10:34:34,803 servicetalk-global-io-executor-1-1 [DEBUG] H2ServerParentConnectionContext - Started HTTP/2 server with prior-knowledge for address /[0:0:0:0:0:0:0:0%0]:8080
 * 2022-01-10 10:34:34,804 servicetalk-global-io-executor-1-1 [DEBUG] DefaultHttpServerBuilder       - Server for address /[0:0:0:0:0:0:0:0%0]:8080 uses strategy DEFAULT_HTTP_EXECUTION_STRATEGY
 * 2022-01-10 10:34:47,649 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] OUTBOUND SETTINGS: ack=false settings={MAX_HEADER_LIST_SIZE=8192}
 * 2022-01-10 10:34:47,654 servicetalk-global-io-executor-1-2 [DEBUG] Recycler                       - -Dio.netty.recycler.maxCapacityPerThread: 4096
 * 2022-01-10 10:34:47,654 servicetalk-global-io-executor-1-2 [DEBUG] Recycler                       - -Dio.netty.recycler.ratio: 8
 * 2022-01-10 10:34:47,654 servicetalk-global-io-executor-1-2 [DEBUG] Recycler                       - -Dio.netty.recycler.chunkSize: 32
 * 2022-01-10 10:34:47,669 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] WRITE: 15B
 * 2022-01-10 10:34:47,671 servicetalk-global-io-executor-1-2 [DEBUG] HttpDebugUtils                 - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] HTTP/2.0 pipeline initialized: TcpServerBinder$2#0, ServiceTalkWireLogger#0, Http2FrameCodec#0, Http2MultiplexHandler#0, H2ServerParentConnectionContext$DefaultH2ServerParentConnection#0, DefaultChannelPipeline$TailContext#0
 * 2022-01-10 10:34:47,671 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] REGISTERED
 * 2022-01-10 10:34:47,672 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] ACTIVE
 * 2022-01-10 10:34:47,672 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] READ_REQUEST
 * 2022-01-10 10:34:47,672 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] READ: 189B
 * 2022-01-10 10:34:47,675 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] INBOUND SETTINGS: ack=false settings={ENABLE_PUSH=0, MAX_CONCURRENT_STREAMS=0, MAX_HEADER_LIST_SIZE=8192}
 * 2022-01-10 10:34:47,676 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] OUTBOUND SETTINGS: ack=true
 * 2022-01-10 10:34:47,676 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] WRITE: 9B
 * 2022-01-10 10:34:47,685 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] INBOUND HEADERS: streamId=3 headers=8 padding=0 endStream=false
 * 2022-01-10 10:34:47,693 servicetalk-global-io-executor-1-2 [DEBUG] CloseHandler                   - io.servicetalk.transport.netty.internal.CloseHandler.LogLevel=null
 * 2022-01-10 10:34:47,768 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] INBOUND DATA: streamId=3 padding=0 endStream=true length=12
 * 2022-01-10 10:34:47,774 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] READ_COMPLETE
 * 2022-01-10 10:34:47,774 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] FLUSH
 * 2022-01-10 10:34:47,775 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] FLUSH
 * 2022-01-10 10:34:47,775 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] READ_REQUEST
 * 2022-01-10 10:34:47,779 servicetalk-global-executor-1-2 [DEBUG] PlatformDependent              - jctools Unbounded/ChunkedArrayQueue: available.
 * 2022-01-10 10:34:47,784 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] READ: 9B
 * 2022-01-10 10:34:47,784 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] INBOUND SETTINGS: ack=true
 * 2022-01-10 10:34:47,786 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] READ_COMPLETE
 * 2022-01-10 10:34:47,786 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] FLUSH
 * 2022-01-10 10:34:47,786 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] READ_REQUEST
 * 2022-01-10 10:34:47,835 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] OUTBOUND HEADERS: streamId=3 headers=4 padding=0 endStream=false
 * 2022-01-10 10:34:47,837 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] WRITE: 9B
 * 2022-01-10 10:34:47,837 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] WRITE: 48B
 * 2022-01-10 10:34:47,845 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] OUTBOUND DATA: streamId=3 padding=0 endStream=false length=18
 * 2022-01-10 10:34:47,845 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] WRITE: 9B
 * 2022-01-10 10:34:47,845 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] WRITE: 18B
 * 2022-01-10 10:34:47,845 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] OUTBOUND HEADERS: streamId=3 headers=1 padding=0 endStream=true
 * 2022-01-10 10:34:47,846 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] WRITE: 9B
 * 2022-01-10 10:34:47,846 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] WRITE: 15B
 * 2022-01-10 10:34:47,847 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] FLUSH
 * 2022-01-10 10:34:47,913 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] READ_COMPLETE
 * 2022-01-10 10:34:47,913 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] FLUSH
 * 2022-01-10 10:34:47,913 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] READ_REQUEST
 * 2022-01-10 10:34:47,916 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] USER_EVENT: class io.netty.channel.socket.ChannelInputShutdownEvent
 * 2022-01-10 10:34:47,919 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] READ_COMPLETE
 * 2022-01-10 10:34:47,919 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] FLUSH
 * 2022-01-10 10:34:47,919 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] READ_REQUEST
 * 2022-01-10 10:34:47,919 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] USER_EVENT: class io.netty.channel.socket.ChannelInputShutdownReadComplete
 * 2022-01-10 10:34:47,919 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 - R:/127.0.0.1:51110] CLOSE
 * 2022-01-10 10:34:47,920 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 ! R:/127.0.0.1:51110] INACTIVE
 * 2022-01-10 10:34:47,920 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x9e2eae25, L:/127.0.0.1:8080 ! R:/127.0.0.1:51110] UNREGISTERED
 * </pre>
 */
public final class DebuggingServer {

    static {
        /*
         * 1. (optional) Disables the AsyncContext associated with individual request/responses to reduce stack-trace
         * depth and simplify execution tracing. This will disable/break some features such as request tracing,
         * authentication, propagated timeouts, etc. that rely upon the AsyncContext so should only be disabled when
         * necessary for debugging:
         */
        // AsyncContext.disable();
    }

    public static void main(String[] args) throws Exception {
        GrpcServers.forPort(8080)
                .initializeHttp(builder -> {
                    builder
                            /*
                             * 2. Disables most asynchronous offloading to simplify execution tracing. Changing this may
                             * significantly change application behavior and introduce unexpected blocking. It is most
                             * useful for being able to directly trace through situations that would normally involve a
                             * thread handoff.
                             */
                            //.executionStrategy(HttpExecutionStrategies.offloadNever())
                            /*
                             * 3. Enables detailed logging of I/O and I/O states, but not payload bodies.
                             * Be sure to also enable the logger in your logging config file,
                             * {@code log4j2.xml} for this example.
                             * Dumping of protocol bodies is disabled to reduce output but can be enabled by using
                             * {@code Boolean.TRUE::booleanValue}.
                             */
                            .enableWireLogging("servicetalk-examples-wire-logger", TRACE, Boolean.FALSE::booleanValue)

                            /*
                             * 4. Enables detailed logging of HTTP2 frames, but not frame contents.
                             * Be sure to also enable the logger in your logging config file,
                             * {@code log4j2.xml} for this example.
                             * Dumping of protocol bodies is disabled to reduce output but can be enabled by using
                             * {@code Boolean.TRUE::booleanValue}.
                             */
                            .protocols(HttpProtocolConfigs.h2()
                                    .enableFrameLogging(
                                            "servicetalk-examples-h2-frame-logger", TRACE, Boolean.FALSE::booleanValue)
                                    .build());
                })
                .listenAndAwait((BlockingGreeterService) (ctx, request) ->
                        HelloReply.newBuilder().setMessage("Hello " + request.getName()).build())
                .awaitShutdown();
    }
}
