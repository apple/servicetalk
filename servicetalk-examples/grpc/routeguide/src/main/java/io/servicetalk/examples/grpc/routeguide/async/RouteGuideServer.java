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
/*
 * Copyright 2015 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.examples.grpc.routeguide.async;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.examples.grpc.routeguide.FeaturesFinder;
import io.servicetalk.examples.grpc.routeguide.RouteSummaryBuilder;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.netty.GrpcServers;

import io.grpc.examples.routeguide.Feature;
import io.grpc.examples.routeguide.Point;
import io.grpc.examples.routeguide.Rectangle;
import io.grpc.examples.routeguide.RouteGuide.RouteGuideService;
import io.grpc.examples.routeguide.RouteNote;
import io.grpc.examples.routeguide.RouteSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.servicetalk.concurrent.api.Publisher.fromIterable;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.examples.grpc.routeguide.FeaturesFinder.fromJson;
import static io.servicetalk.examples.grpc.routeguide.FeaturesFinder.randomFeatures;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.function.Function.identity;

public final class RouteGuideServer {
    public static void main(String[] args) throws Exception {
        FeaturesFinder featuresFinder = args.length > 0 ?
                fromJson(RouteGuideServer.class.getResource(args[0])) :
                randomFeatures();
        GrpcServers.forPort(8080)
                .listenAndAwait(new DefaultRouteGuideService(featuresFinder))
                .awaitShutdown();
    }

    private static final class DefaultRouteGuideService implements RouteGuideService {
        private final FeaturesFinder featuresFinder;
        private final ConcurrentMap<Point, List<RouteNote>> routeNotes = new ConcurrentHashMap<>();

        DefaultRouteGuideService(final FeaturesFinder featuresFinder) {
            this.featuresFinder = featuresFinder;
        }

        @Override
        public Single<Feature> getFeature(final GrpcServiceContext ctx, final Point request) {
            Feature feature = featuresFinder.findFeature(request);
            return feature != null ? succeeded(feature) : succeeded(Feature.newBuilder().build());
        }

        @Override
        public Publisher<Feature> listFeatures(final GrpcServiceContext ctx, final Rectangle request) {
            int left = min(request.getLo().getLongitude(), request.getHi().getLongitude());
            int right = max(request.getLo().getLongitude(), request.getHi().getLongitude());
            int top = max(request.getLo().getLatitude(), request.getHi().getLatitude());
            int bottom = min(request.getLo().getLatitude(), request.getHi().getLatitude());
            return fromIterable(featuresFinder)
                    .filter(feature -> {
                        if (feature != null && !feature.getName().isEmpty()) {
                            int lat = feature.getLocation().getLatitude();
                            int lon = feature.getLocation().getLongitude();
                            return lon >= left && lon <= right && lat >= bottom && lat <= top;
                        }
                        return false;
                    });
        }

        @Override
        public Single<RouteSummary> recordRoute(final GrpcServiceContext ctx, final Publisher<Point> request) {
            return request.collect(() -> new RouteSummaryBuilder(featuresFinder),
                    RouteSummaryBuilder::consume).map(RouteSummaryBuilder::buildSummary);
        }

        @Override
        public Publisher<RouteNote> routeChat(final GrpcServiceContext ctx, final Publisher<RouteNote> request) {
            return request.map(routeNote -> {
                Point location = routeNote.getLocation();
                List<RouteNote> notes = routeNotes.computeIfAbsent(location, point -> new CopyOnWriteArrayList<>());
                List<RouteNote> copy = new ArrayList<>(notes);
                notes.add(routeNote);
                return copy;
            }).flatMapConcatIterable(identity());
        }
    }
}
