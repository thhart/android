/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.logcat

import com.android.sdklib.AndroidApiLevel
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.service.LogcatService
import java.time.Duration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import java.util.concurrent.atomic.AtomicInteger

internal class FakeLogcatService : LogcatService {
  private var channel: Channel<List<LogcatMessage>>? = null

  var invocations = AtomicInteger()

  val clearRequests = mutableListOf<String>()

  suspend fun logMessages(vararg messages: LogcatMessage) {
    channel?.send(messages.asList())
      ?: throw IllegalStateException("Channel not setup. Did you call readLogcat()?")
  }

  override suspend fun readLogcat(
    serialNumber: String,
    sdk: AndroidApiLevel,
    duration: Duration,
    newMessagesOnly: Boolean,
  ): Flow<List<LogcatMessage>> {
    return Channel<List<LogcatMessage>>(1)
      .also {
        channel = it
        invocations.incrementAndGet()
      }
      .consumeAsFlow()
  }

  override suspend fun clearLogcat(serialNumber: String) {
    clearRequests.add(serialNumber)
  }
}
