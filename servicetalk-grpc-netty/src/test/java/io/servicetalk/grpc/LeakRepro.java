package io.servicetalk.grpc;

import com.apple.servicetalkleak.Message;
import com.apple.servicetalkleak.ServiceTalkLeak;
import io.netty.buffer.ByteBufUtil;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.api.GrpcStatusCode;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.netty.GrpcClients;
import io.servicetalk.grpc.netty.GrpcServers;
import io.servicetalk.http.netty.HttpProtocolConfigs;
import io.servicetalk.http.netty.SpliceFlatStreamToMetaSingle;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.netty.internal.NettyIoExecutors;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.servicetalk.concurrent.api.internal.BlockingUtils.blockingInvocation;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class LeakRepro {

    private static final Logger LOGGER = LoggerFactory.getLogger(LeakRepro.class);

    static boolean leakDetected = false;

    static {
        System.setProperty("io.servicetalk.http.netty.leakdetection", "strict");
        System.setProperty("io.netty.leakDetection.level", "paranoid");
        ByteBufUtil.setLeakListener((type, records) -> {
            leakDetected = true;
            LOGGER.error("ByteBuf leak detected!");
        });
    }

    IoExecutor serverExecutor = NettyIoExecutors.createIoExecutor(1, "server");
    IoExecutor clientExecutor = NettyIoExecutors.createIoExecutor(1, "client");

    @SuppressWarnings("resource")
    @Test
    public void testLeak() throws Exception {
        GrpcServers.forPort(8888)
                .initializeHttp(b -> b
                        .ioExecutor(serverExecutor)
                        .executor(serverExecutor))
                .listenAndAwait(new ServiceTalkLeak.ServiceTalkLeakService() {
                    @Override
                    public Publisher<Message> rpc(GrpcServiceContext ctx, Publisher<Message> request) {
                        Publisher<Message> response = splice(request)
                                .flatMapPublisher(pair -> {
                                    LOGGER.info("Initial message: " + pair.head);
                                    return Publisher.failed(new GrpcStatusException(GrpcStatusCode.INVALID_ARGUMENT.status()));
                                });
                        return response;
                    }
                });

        ServiceTalkLeak.ServiceTalkLeakClient client = GrpcClients.forAddress(HostAndPort.of("127.0.0.1", 8888))
                .initializeHttp(b -> b
                        .protocols(HttpProtocolConfigs.h2().enableFrameLogging("CLIENT", LogLevel.INFO, () -> true).build())
                        .ioExecutor(clientExecutor)
                        .executor(clientExecutor))
                .build(new ServiceTalkLeak.ClientFactory());

        for (int i = 0; i < 10; i++) {
            LOGGER.info("Iteration {}", i);
            blockingInvocation(
                    client.rpc(
                            Publisher.from(
                                    Message.newBuilder().setValue("first message").build(),
                                    Message.newBuilder().setValue("second message (which leaks)").build()))
                    .ignoreElements()
                    .onErrorComplete());

            System.gc();
            System.runFinalization();
        }

        assertFalse(leakDetected);
    }

    private static Single<Pair> splice(Publisher<Message> request) {
        return request.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Pair::new));
    }

    private static final class Pair {
        final Message head;
        final Publisher<Message> stream;

        public Pair(Message head, Publisher<Message> stream) {
            this.head = head;
            this.stream = stream;
        }
    }
}