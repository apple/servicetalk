package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.HttpApiConversions;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadAll;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.http.api.HttpSerializers.appSerializerUtf8FixLen;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpServerDefaultExecutionStrategyTest {

    private static Stream<Arguments> services() {
        return Stream.of(offloadNone(),
                        HttpExecutionStrategies.customStrategyBuilder().offloadSend().build(),
                        offloadAll())
                .map((HttpExecutionStrategy hes) -> new StreamingHttpService() {
                    @Override
                    public Single<StreamingHttpResponse> handle(HttpServiceContext ctx, StreamingHttpRequest request, StreamingHttpResponseFactory responseFactory) {
                        return succeeded(responseFactory.ok()
                                .payloadBody(from("Hello World!"), appSerializerUtf8FixLen()));
                    }

                    @Override
                    public HttpExecutionStrategy requiredOffloads() {
                        return hes;
                    }

                    public String toString() {
                        return hes.toString();
                    }
                })
                .map(service -> Arguments.of(service));
    }

    @ParameterizedTest(name = "{displayName} {index}: service: {0}")
    @MethodSource("services")
    void testHttpService(StreamingHttpService streamingAsyncService) {
        HttpService aggregateService = HttpApiConversions.toHttpService(streamingAsyncService);
        HttpExecutionStrategy serviceStrategy = aggregateService.requiredOffloads();
        assertTrue(serviceStrategy.isSendOffloaded(), "Unexpected send strategy " + serviceStrategy);
        assertFalse(serviceStrategy.isMetadataReceiveOffloaded(), "Unexpected meta strategy " + serviceStrategy);
        assertTrue(serviceStrategy.isDataReceiveOffloaded(), "Unexpected read strategy " + serviceStrategy);
    }

    @ParameterizedTest(name = "{displayName} {index}: service: {0}")
    @MethodSource("services")
    void testBlockingHttpService(StreamingHttpService streamingAsyncService) {
        BlockingHttpService blockingHttpService =
                HttpApiConversions.toBlockingHttpService(streamingAsyncService);
        HttpExecutionStrategy serviceStrategy = blockingHttpService.requiredOffloads();
        assertFalse(serviceStrategy.isSendOffloaded(), "Unexpected send strategy " + serviceStrategy);
        assertFalse(serviceStrategy.isMetadataReceiveOffloaded(), "Unexpected meta strategy " + serviceStrategy);
        assertTrue(serviceStrategy.isDataReceiveOffloaded(), "Unexpected read strategy " + serviceStrategy);
    }

    @ParameterizedTest(name = "{displayName} {index}: service: {0}")
    @MethodSource("services")
    void testBlockingStreamingHttpService(StreamingHttpService streamingAsyncService) {
        BlockingStreamingHttpService blockingStreamingHttpService =
                HttpApiConversions.toBlockingStreamingHttpService(streamingAsyncService);
        HttpExecutionStrategy serviceStrategy = blockingStreamingHttpService.requiredOffloads();
        assertFalse(serviceStrategy.isSendOffloaded(), "Unexpected send strategy " + serviceStrategy);
        assertTrue(serviceStrategy.isMetadataReceiveOffloaded(), "Unexpected meta strategy " + serviceStrategy);
        assertFalse(serviceStrategy.isDataReceiveOffloaded(), "Unexpected read strategy " + serviceStrategy);
    }
}
