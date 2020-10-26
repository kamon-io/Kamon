/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
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