/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.rendering

import com.android.tools.perflogger.Metric
import java.time.Instant
import libcore.util.NativeAllocationRegistry

class PerfgateRenderMetric {

  private var mTimestamp: Long = 0
  private var mPrevUsedMem: Long = 0
  private var mMemoryUsage: Long = 0
  private var mStartTime: Long = 0
  private var mElapsedTime: Long = 0
  private var mPrevUsedLayoutlibNativeMem: Long = 0
  private var mLayoutlibNativeMemoryUsage: Long = 0

  val renderTimeMetricSample: Metric.MetricSample
    get() = Metric.MetricSample(mTimestamp, mElapsedTime)

  val memoryMetricSample: Metric.MetricSample
    get() = Metric.MetricSample(mTimestamp, mMemoryUsage)

  val layoutlibNativeMemoryMetricSample: Metric.MetricSample
    get() = Metric.MetricSample(mTimestamp, mLayoutlibNativeMemoryUsage)

  fun beforeTest() {
    mPrevUsedMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
    mStartTime = System.currentTimeMillis()
    mPrevUsedLayoutlibNativeMem =
      NativeAllocationRegistry.getMetrics().sumOf { it.mallocedBytes + it.nonmallocedBytes }
  }

  fun afterTest() {
    mElapsedTime = System.currentTimeMillis() - mStartTime
    mMemoryUsage =
      Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() - mPrevUsedMem
    mLayoutlibNativeMemoryUsage =
      NativeAllocationRegistry.getMetrics().sumOf { it.mallocedBytes + it.nonmallocedBytes } -
        mPrevUsedLayoutlibNativeMem

    mTimestamp = Instant.now().toEpochMilli()
  }
}
