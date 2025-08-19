/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.loadbalancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;

/**
 * A {@link Subsetter} that takes a random subset of the provided hosts.
 */
final class RandomSubsetter implements Subsetter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RandomSubsetter.class);

    private final int randomSubsetSize;
    private final String lbDescription;

    private RandomSubsetter(final int randomSubsetSize, final String lbDescription) {
        this.randomSubsetSize = ensurePositive(randomSubsetSize, "randomSubsetSize");
        this.lbDescription = lbDescription;
    }

    @Override
    public <T extends PrioritizedHost> List<T> subset(List<T> allHosts) {
        if (allHosts.size() <= randomSubsetSize) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("{}: Host set size of {} is less than designated subset size of {}, returning all hosts",
                        lbDescription, allHosts.size(), randomSubsetSize);
            }
            return allHosts;
        }

        // We need to sort, and then return the list with the subsetSize number of healthy elements.
        List<T> result = new ArrayList<>(allHosts);
        result.sort(Comparator.comparingLong(PrioritizedHost::randomSeed));

        // We don't want to consider the unhealthy elements to be a part of our subset, so we're going to grow it
        // to account for un-health endpoints. However, we need to know how many that is.
        int healthyCount = 0;
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i).isHealthy()) {
                ++healthyCount;
                if (healthyCount == randomSubsetSize) {
                    // Trim elements after i to form the subset.
                    while (result.size() > i + 1) {
                        result.remove(result.size() - 1);
                    }
                    break;
                }
            }
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("{}: Using {} out of a total {} hosts ({} considered unhealthy). Used hosts: {}",
                    lbDescription, result.size(), allHosts.size(), result.size() - healthyCount, result);
        }
        return result;
    }

    @Override
    public String toString() {
        return "RandomSubsetter{" +
                "randomSubsetSize=" + randomSubsetSize +
                '}';
    }

    static final class RandomSubsetterFactory implements Subsetter.SubsetterFactory {

        private final int randomSubsetSize;

        RandomSubsetterFactory(int randomSubsetSize) {
            this.randomSubsetSize = ensurePositive(randomSubsetSize, "randomSubsetSize");
        }

        @Override
        public Subsetter newSubsetter(String lbDescription) {
            return new RandomSubsetter(randomSubsetSize, lbDescription);
        }

        @Override
        public String toString() {
            return "RandomSubsetterFactory{" +
                    "randomSubsetSize=" + randomSubsetSize +
                    '}';
        }
    }
}
