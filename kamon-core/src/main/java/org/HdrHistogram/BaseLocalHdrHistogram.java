package org.HdrHistogram;

import kamon.metric.DynamicRange;

/**
 * Exposes internal state from the org.HdrHistogram.Histogram class.
 */
public class BaseLocalHdrHistogram extends Histogram implements HdrHistogramInternalState {

  public BaseLocalHdrHistogram(DynamicRange dynamicRange) {
    super(dynamicRange.lowestDiscernibleValue(), dynamicRange.highestTrackableValue(), dynamicRange.significantValueDigits());
    setAutoResize(true);
  }

  @Override
  void incrementTotalCount() {
    // We don't need to track the total count so this is just disabled.
  }

  @Override
  void addToTotalCount(long value) {
    // We don't need to track the total count so this is just disabled.
  }

  @Override
  public int getCountsArraySize() {
    return super.counts.length;
  }

  @Override
  public long getFromCountsArray(int index) {
    return super.counts[index];
  }

  @Override
  public long getAndSetFromCountsArray(int index, long newValue) {
    long value = super.counts[index];
    super.counts[index] = newValue;
    return value;
  }

  @Override
  public int getUnitMagnitude() {
    return super.unitMagnitude;
  }

  @Override
  public int getSubBucketHalfCount() {
    return super.subBucketHalfCount;
  }

  @Override
  public int getSubBucketHalfCountMagnitude() {
    return super.subBucketHalfCountMagnitude;
  }
}
