/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.streaming.emulator

import com.android.annotations.concurrency.AnyThread
import com.android.tools.idea.io.grpc.stub.StreamObserver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

/** A [StreamObserver] that exposes the result of the gRPC call as a [Deferred]. */
class DeferredResultStreamObserver<T> : EmptyStreamObserver<T>() {

  val deferredResult: Deferred<T>
    get() = completableDeferredResult

  private val completableDeferredResult = CompletableDeferred<T>()

  @AnyThread
  override fun onNext(message: T) {
    completableDeferredResult.complete(message)
  }

  @AnyThread
  override fun onError(t: Throwable) {
    completableDeferredResult.completeExceptionally(t)
  }
}