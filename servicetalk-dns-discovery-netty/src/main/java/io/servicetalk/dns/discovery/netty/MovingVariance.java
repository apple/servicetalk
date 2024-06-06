package io.servicetalk.dns.discovery.netty;

import java.util.Arrays;

final class MovingVariance {

    private final int size;
    private final double invSize;
    private final double invSizeBySizeMinus1;

    // We initialize with the assumption that all previous sames were zero. This lets us know the sum (x[i]) == 0
    // and that Var(x[n]) == 0. However, that means that until `size` samples the variance will be artificially low.
    private final int[] xi;
    private int ii;
    private long sumXi;
    private long varianceSizeSizeMinus1;

    MovingVariance(final int size) {
        this(size, Integer.MAX_VALUE);
    }

    MovingVariance(final int size, final int initialMean) {
        if (size < 2) {
            throw new IllegalArgumentException("Must allow at least two samples");
        }
        this.size = size;
        this.invSize = 1.0 / size;
        this.invSizeBySizeMinus1 = 1.0 / (size * (size - 1));
        this.xi = new int[size];
        Arrays.fill(xi, initialMean);
        sumXi = ((long) initialMean) * size;
    }

    public double mean() {
        return sumXi * invSize;
    }

    public double variance() {
        return varianceSizeSizeMinus1 * invSizeBySizeMinus1;
    }

    public double stdev() {
        return Math.sqrt(variance());
    }

    public void addSample(int sample) {
        // Widen sample to a long so that we don't have to worry about overflows.
        final long xn = sample;
        final int i = getIndex();
        final long x0 = xi[i];
        xi[i] = sample;
        final long oldSumXi = sumXi;
        sumXi += xn - x0;
        varianceSizeSizeMinus1 = varianceSizeSizeMinus1 + (size * (xn + x0) - sumXi - oldSumXi) * (xn - x0);
    }

    private int getIndex() {
        final int result = ii;
        if (++ii == size) {
            ii = 0;
        }
        return result;
    }
}
