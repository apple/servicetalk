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
package io.servicetalk.examples.http.service.composition.backends;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.examples.http.service.composition.backends.MetadataBackend.newMetadataService;
import static io.servicetalk.examples.http.service.composition.backends.PortRegistry.METADATA_BACKEND_ADDRESS;
import static io.servicetalk.examples.http.service.composition.backends.PortRegistry.RATINGS_BACKEND_ADDRESS;
import static io.servicetalk.examples.http.service.composition.backends.PortRegistry.RECOMMENDATIONS_BACKEND_ADDRESS;
import static io.servicetalk.examples.http.service.composition.backends.PortRegistry.USER_BACKEND_ADDRESS;
import static io.servicetalk.examples.http.service.composition.backends.RatingBackend.newRatingService;
import static io.servicetalk.examples.http.service.composition.backends.RecommendationBackend.newRecommendationsService;
import static io.servicetalk.examples.http.service.composition.backends.UserBackend.newUserService;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;

/**
 * A server starter for all backends in this example.
 */
public final class BackendsStarter {

    public static final String RECOMMENDATION_SERVICE_NAME = "recommendation-service";
    public static final String METADATA_SERVICE_NAME = "metadata-service";
    public static final String USER_SERVICE_NAME = "user-service";
    public static final String RATING_SERVICE_NAME = "rating-service";

    private BackendsStarter() {
        // No instances.
    }

    public static void main(String[] args) throws Exception {
        // Create an AutoCloseable representing all resources used in this example.
        try (CompositeCloseable resources = newCompositeCloseable()) {
            // Shared IoExecutor for the application.
            IoExecutor ioExecutor = resources.prepend(createIoExecutor());

            // This is a single Completable used to await closing of all backends started by this class. It is used to
            // provide a way to not let main() exit.
            Completable allServicesOnClose = completed();

            BackendStarter starter = new BackendStarter(ioExecutor, resources);
            final ServerContext recommendationService =
                    starter.start(RECOMMENDATIONS_BACKEND_ADDRESS.port(), RECOMMENDATION_SERVICE_NAME,
                            newRecommendationsService());
            allServicesOnClose = allServicesOnClose.merge(recommendationService.onClose());

            final ServerContext metadataService =
                    starter.start(METADATA_BACKEND_ADDRESS.port(), METADATA_SERVICE_NAME, newMetadataService());
            allServicesOnClose = allServicesOnClose.merge(metadataService.onClose());

            final ServerContext userService =
                    starter.start(USER_BACKEND_ADDRESS.port(), USER_SERVICE_NAME, newUserService());
            allServicesOnClose = allServicesOnClose.merge(userService.onClose());

            final ServerContext ratingService =
                    starter.start(RATINGS_BACKEND_ADDRESS.port(), RATING_SERVICE_NAME, newRatingService());
            allServicesOnClose = allServicesOnClose.merge(ratingService.onClose());

            // Await termination of all backends started by this class.
            allServicesOnClose.toFuture().get();
        }
    }
}
