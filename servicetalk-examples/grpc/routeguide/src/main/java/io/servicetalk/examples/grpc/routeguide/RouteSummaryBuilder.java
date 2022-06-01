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
package io.servicetalk.examples.grpc.routeguide;

import io.grpc.examples.routeguide.Feature;
import io.grpc.examples.routeguide.Point;
import io.grpc.examples.routeguide.RouteSummary;

import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.Math.toRadians;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public final class RouteSummaryBuilder {

    private final FeaturesFinder featuresFinder;
    private int pointCount;
    private int featureCount;
    private int distance;
    private Point previous;
    final long startTime = System.nanoTime();

    public RouteSummaryBuilder(final FeaturesFinder featuresFinder) {
        this.featuresFinder = featuresFinder;
    }

    public RouteSummaryBuilder consume(Point nextPoint) {
        ++pointCount;
        Feature feature = featuresFinder.findFeature(nextPoint);
        if (feature != null) {
            ++featureCount;
        }
        if (previous != null) {
            distance += calcDistance(previous, nextPoint);
        }
        previous = nextPoint;
        return this;
    }

    public RouteSummary buildSummary() {
        long elapsedTime = NANOSECONDS.toSeconds(System.nanoTime() - startTime);
        return RouteSummary.newBuilder().setPointCount(pointCount)
                .setFeatureCount(featureCount).setDistance(distance)
                .setElapsedTime((int) elapsedTime).build();
    }

    /**
     * Calculate the distance between two points using the "haversine" formula.
     * The formula is based on https://en.wikipedia.org/wiki/Haversine_formula.
     *
     * @param start The starting point
     * @param end The end point
     * @return The distance between the points in meters
     */
    private int calcDistance(Point start, Point end) {
        int r = 6371000; // earth radius in meters
        double lat1 = toRadians(start.getLatitude());
        double lat2 = toRadians(end.getLatitude());
        double lon1 = toRadians(start.getLongitude());
        double lon2 = toRadians(end.getLongitude());
        double deltaLat = lat2 - lat1;
        double deltaLon = lon2 - lon1;

        double a = sin(deltaLat / 2) * sin(deltaLat / 2)
                + cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2);
        double c = 2 * atan2(sqrt(a), sqrt(1 - a));

        return (int) (r * c);
    }
}
