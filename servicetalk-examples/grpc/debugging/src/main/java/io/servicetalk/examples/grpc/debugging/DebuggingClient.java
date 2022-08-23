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

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.grpc.netty.GrpcClients;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.netty.H2ProtocolConfigBuilder;
import io.servicetalk.http.netty.HttpProtocolConfigs;
import io.servicetalk.logging.api.LogLevel;

import io.grpc.examples.debugging.Greeter.BlockingGreeterClient;
import io.grpc.examples.debugging.Greeter.ClientFactory;
import io.grpc.examples.debugging.HelloReply;
import io.grpc.examples.debugging.HelloRequest;

import java.util.function.BooleanSupplier;

import static io.servicetalk.logging.api.LogLevel.TRACE;

/**
 * The blocking "Hello World" example with debugging features enabled. Five debugging features are demonstrated:
 * <ol>
 *     <li>Disabling {@link AsyncContext}</li>
 *     <li>Disabling {@link HttpExecutionStrategy offloading}</li>
 *     <li>Enabling {@link SingleAddressHttpClientBuilder#enableWireLogging(String, LogLevel, BooleanSupplier) HTTP wire logging}</li>
 *     <li>Enabling {@link H2ProtocolConfigBuilder#enableFrameLogging(String, LogLevel, BooleanSupplier) HTTP/2 frame logging}</li>
 *     <li>Enabling additional logger verbosity in the {@code log4j2.xml} configuration file</li>
 * </ol>
 * <p>The wire and frame logging features require that you configure a logger with an appropriate log level. For this
 * example {@code log4j2.xml} is used by both the client and server and configures the
 * {@code servicetalk-examples-wire-logger} and {@code servicetalk-examples-h2-frame-logger} loggers at
 * {@link io.servicetalk.logging.api.LogLevel#TRACE TRACE} level. Wire logging is configured to include logging of all
 * user data contained in the payload, see {@link #LOG_USER_DATA}.
 *
 * <p>When configured correctly the output should be similar to the following:
 * <pre>
 * 2022-01-10 10:34:46,942                           main [DEBUG] AsyncContext                   - Enabled.
 * 2022-01-10 10:34:47,224                           main [DEBUG] GlobalDnsServiceDiscoverer     - Initialized HostAndPortClientInitializer
 * 2022-01-10 10:34:47,324                           main [DEBUG] DefaultSingleAddressHttpClientBuilder - Client for localhost:8080 created with base strategy DEFAULT_HTTP_EXECUTION_STRATEGY → computed strategy DEFAULT_HTTP_EXECUTION_STRATEGY
 * 2022-01-10 10:34:47,334 servicetalk-global-io-executor-1-1 [DEBUG] DefaultDnsClient               - DnsClient io.servicetalk.dns.discovery.netty.DefaultDnsClient@1a83d58e, sending events for address: A* lookups for localhost (size 1) [DefaultServiceDiscovererEvent{address=localhost/127.0.0.1, status=available}].
 * 2022-01-10 10:34:47,334 servicetalk-global-io-executor-1-1 [DEBUG] RoundRobinLoadBalancer         - Load balancer for localhost:8080#1: received new ServiceDiscoverer event DefaultServiceDiscovererEvent{address=localhost/127.0.0.1:8080, status=available}. Inferred status: available.
 * 2022-01-10 10:34:47,344 servicetalk-global-io-executor-1-1 [DEBUG] RoundRobinLoadBalancer         - Load balancer for localhost:8080#1: now using 1 addresses: [Host{address=localhost/127.0.0.1:8080, state=ACTIVE(failedConnections=0), #connections=0}].
 * 2022-01-10 10:34:47,526 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x1ae4aa52] REGISTERED
 * 2022-01-10 10:34:47,526 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x1ae4aa52] CONNECT: localhost/127.0.0.1:8080
 * 2022-01-10 10:34:47,530 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] ACTIVE
 * 2022-01-10 10:34:47,531 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] WRITE: 24B
 * 2022-01-10 10:34:47,534 servicetalk-global-io-executor-1-2 [DEBUG] Recycler                       - -Dio.netty.recycler.maxCapacityPerThread: 4096
 * 2022-01-10 10:34:47,534 servicetalk-global-io-executor-1-2 [DEBUG] Recycler                       - -Dio.netty.recycler.ratio: 8
 * 2022-01-10 10:34:47,534 servicetalk-global-io-executor-1-2 [DEBUG] Recycler                       - -Dio.netty.recycler.chunkSize: 32
 * 2022-01-10 10:34:47,536 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] OUTBOUND SETTINGS: ack=false settings={ENABLE_PUSH=0, MAX_CONCURRENT_STREAMS=0, MAX_HEADER_LIST_SIZE=8192}
 * 2022-01-10 10:34:47,552 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] WRITE: 27B
 * 2022-01-10 10:34:47,553 servicetalk-global-io-executor-1-2 [DEBUG] HttpDebugUtils                 - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] HTTP/2.0 pipeline initialized: ServiceTalkWireLogger#0, Http2FrameCodec#0, Http2MultiplexHandler#0, H2ClientParentConnectionContext$DefaultH2ClientParentConnection#0, DefaultChannelPipeline$TailContext#0
 * 2022-01-10 10:34:47,562 servicetalk-global-io-executor-1-2 [DEBUG] PlatformDependent              - jctools Unbounded/ChunkedArrayQueue: available.
 * 2022-01-10 10:34:47,587 servicetalk-global-io-executor-1-2 [DEBUG] CloseHandler                   - io.servicetalk.transport.netty.internal.CloseHandler.LogLevel=null
 * 2022-01-10 10:34:47,639 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] OUTBOUND HEADERS: streamId=3 headers=8 padding=0 endStream=false
 * 2022-01-10 10:34:47,644 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] WRITE: 9B
 * 2022-01-10 10:34:47,645 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] WRITE: 108B
 * 2022-01-10 10:34:47,657 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] OUTBOUND DATA: streamId=3 padding=0 endStream=true length=12
 * 2022-01-10 10:34:47,657 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] WRITE: 9B
 * 2022-01-10 10:34:47,657 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] WRITE: 12B
 * 2022-01-10 10:34:47,658 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] FLUSH
 * 2022-01-10 10:34:47,661 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] READ_REQUEST
 * 2022-01-10 10:34:47,777 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] READ: 24B
 * 2022-01-10 10:34:47,780 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] INBOUND SETTINGS: ack=false settings={MAX_HEADER_LIST_SIZE=8192}
 * 2022-01-10 10:34:47,784 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] OUTBOUND SETTINGS: ack=true
 * 2022-01-10 10:34:47,784 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] WRITE: 9B
 * 2022-01-10 10:34:47,784 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] FLUSH
 * 2022-01-10 10:34:47,784 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] INBOUND SETTINGS: ack=true
 * 2022-01-10 10:34:47,784 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] READ_COMPLETE
 * 2022-01-10 10:34:47,785 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] FLUSH
 * 2022-01-10 10:34:47,785 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] READ_REQUEST
 * 2022-01-10 10:34:47,847 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] READ: 108B
 * 2022-01-10 10:34:47,849 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] INBOUND HEADERS: streamId=3 headers=4 padding=0 endStream=false
 * 2022-01-10 10:34:47,858 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] INBOUND DATA: streamId=3 padding=0 endStream=false length=18
 * 2022-01-10 10:34:47,859 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] INBOUND HEADERS: streamId=3 headers=1 padding=0 endStream=true
 * 2022-01-10 10:34:47,861 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] READ_COMPLETE
 * 2022-01-10 10:34:47,861 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] FLUSH
 * 2022-01-10 10:34:47,861 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] FLUSH
 * 2022-01-10 10:34:47,861 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] READ_REQUEST
 * message: "Hello World"
 *
 * 2022-01-10 10:34:47,913 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 - R:localhost/127.0.0.1:8080] CLOSE
 * 2022-01-10 10:34:47,914 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 ! R:localhost/127.0.0.1:8080] INACTIVE
 * 2022-01-10 10:34:47,914 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x1ae4aa52, L:/127.0.0.1:51110 ! R:localhost/127.0.0.1:8080] UNREGISTERED
 * </pre>
 */
public final class DebuggingClient {

    static {
        /*
         * 1. (optional) Disables the AsyncContext associated with individual request/responses to reduce stack-trace
         * depth and simplify execution tracing. This will disable/break some features such as request tracing,
         * authentication, propagated timeouts, etc. that rely upon the AsyncContext so should only be disabled when
         * necessary for debugging:
         */
        // AsyncContext.disable();
    }

    /**
     * Logging of protocol user data is disabled to reduce output but can be enabled by using
     * {@code Boolean.TRUE::booleanValue}. Logging user data may expose sensitive content contained in the headers or
     * payload body. Care and consideration should be taken before enabling this feature on production systems.
     *
     * <p>If {@link Boolean#TRUE} then all user data in the headers and payload bodies will be logged in addition to
     * network events.
     * <p>If {@link Boolean#FALSE} then only network events will be logged, but user data contents will be omitted.
     *
     * <p>This implementation uses a constant function to enable or disable logging of user data, your implementation
     * could selectively choose at runtime to log user data based upon application state or context.</p>
     */
    static final BooleanSupplier LOG_USER_DATA = Boolean.FALSE::booleanValue;

    public static void main(String[] args) throws Exception {
        try (BlockingGreeterClient client = GrpcClients.forAddress("localhost", 8080)
                .initializeHttp(builder -> builder
                        /*
                         * 2. (optional) Disables most asynchronous offloading to simplify execution tracing. Changing
                         * this may significantly change application behavior and introduce unexpected blocking. It is
                         * most useful for being able to directly trace through situations that would normally involve a
                         * thread handoff.
                         */
                        // .executionStrategy(HttpExecutionStrategies.offloadNever())

                        /*
                         * 3. Enables detailed logging of I/O events and I/O states.
                         *
                         * Be sure to also enable the TRACE logger in your logging config file (log4j2.xml for this
                         * example) or raise the configured logging level (2nd argument) to INFO/WARNING to get
                         * visibility without modifying the logger config.
                         * Dumping of protocol bodies is disabled to reduce output but can be enabled by using
                         * {@code Boolean.TRUE::booleanValue} as the 3rd argument. Note that logging all data may leak
                         * sensitive information into logs output. Be careful enabling data logging in production
                         * environments.
                         */
                        .enableWireLogging("servicetalk-examples-wire-logger", TRACE, LOG_USER_DATA)

                        /*
                         * 4. Enables detailed logging of HTTP/2 frames.
                         *
                         * Be sure to also enable the TRACE logger in your logging config file (log4j2.xml for this
                         * example) or raise the configured logging level (2nd argument) to INFO/WARNING to get
                         * visibility without modifying the logger config.
                         * Dumping of protocol bodies is disabled to reduce output but can be enabled by using
                         * {@code Boolean.TRUE::booleanValue} as the 3rd argument. Note that logging all data may leak
                         * sensitive information into logs output. Be careful enabling data logging in production
                         * environments.
                         */
                        .protocols(HttpProtocolConfigs.h2()
                                .enableFrameLogging(
                                        "servicetalk-examples-h2-frame-logger", TRACE, LOG_USER_DATA)
                                .build()))
                .buildBlocking(new ClientFactory())) {
            HelloReply reply = client.sayHello(HelloRequest.newBuilder().setName("World").build());
            System.out.println(reply);
        }
    }
}
