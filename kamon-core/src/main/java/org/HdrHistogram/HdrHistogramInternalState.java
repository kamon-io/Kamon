package org.HdrHistogram;


/**
 * This interface is used as an extension to org.HdrHistogram types for the only purpose of accessing internal state
 * that is not visible outside of the org.HdrHistogram package.
 */
public interface HdrHistogramInternalState {

  int getCountsArraySize();

  long getFromCountsArray(int index);

  long getAndSetFromCountsArray(int index, long newValue);

  int getUnitMagnitude();

  int getSubBucketHalfCount();

  int getSubBucketHalfCountMagnitude();

}