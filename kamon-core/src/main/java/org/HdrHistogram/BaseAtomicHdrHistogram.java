package org.HdrHistogram;

import java.nio.ByteBuffer;

import kamon.metric.DynamicRange;

/**
 * Exposes internal state from the org.HdrHistogram.AtomicHistogram class.
 * <p>
 * Could extend AtomicHistogram and avoid the delegate pattern, but for issue https://github.com/scala/bug/issues/11575
 */
public class BaseAtomicHdrHistogram extends AbstractHistogramBase implements HdrHistogramInternalState {

    private AtomicHistogram delegate;

    public BaseAtomicHdrHistogram(DynamicRange dynamicRange) {
        this.delegate = new AtomicHistogram(dynamicRange.lowestDiscernibleValue(), dynamicRange.highestTrackableValue(), dynamicRange.significantValueDigits());
    }

    public long getHighestTrackableValue() {
        return delegate.highestTrackableValue;
    }

    public void recordValue(long value) throws ArrayIndexOutOfBoundsException {
        delegate.recordValue(value);
    }

    public void recordValueWithCount(long value, long count) throws ArrayIndexOutOfBoundsException {
        delegate.recordValueWithCount(value, count);
    }

    public void reset() {
        delegate.reset();
    }

    @Override
    public int getNeededByteBufferCapacity() {
        return delegate.getNeededByteBufferCapacity();
    }

    @Override
    public int encodeIntoCompressedByteBuffer(final ByteBuffer targetBuffer, int compressionLevel) {
        return delegate.encodeIntoCompressedByteBuffer(targetBuffer, compressionLevel);
    }

    @Override
    public long getStartTimeStamp() {
        return delegate.getStartTimeStamp();
    }

    @Override
    public void setStartTimeStamp(long startTimeStamp) {
        delegate.setStartTimeStamp(startTimeStamp);
    }

    @Override
    public long getEndTimeStamp() {
        return delegate.getEndTimeStamp();
    }

    @Override
    public void setEndTimeStamp(long startTimeStamp) {
        delegate.setEndTimeStamp(startTimeStamp);
    }

    @Override
    public String getTag() {
        return delegate.getTag();
    }

    @Override
    public void setTag(String tag) {
        delegate.setTag(tag);
    }

    @Override
    public double getMaxValueAsDouble() {
        return delegate.getMaxValueAsDouble();
    }

    @Override
    void setIntegerToDoubleValueConversionRatio(double integerToDoubleValueConversionRatio) {
        delegate.setIntegerToDoubleValueConversionRatio(integerToDoubleValueConversionRatio);
    }

    @Override
    public int getCountsArraySize() {
        return delegate.counts.length();
    }

    @Override
    public long getFromCountsArray(int index) {
        return delegate.counts.get(index);
    }

    @Override
    public long getAndSetFromCountsArray(int index, long newValue) {
        return delegate.counts.getAndSet(index, newValue);
    }

    @Override
    public int getUnitMagnitude() {
        return delegate.unitMagnitude;
    }

    @Override
    public int getSubBucketHalfCount() {
        return delegate.subBucketHalfCount;
    }

    @Override
    public int getSubBucketHalfCountMagnitude() {
        return delegate.subBucketHalfCountMagnitude;
    }
}
