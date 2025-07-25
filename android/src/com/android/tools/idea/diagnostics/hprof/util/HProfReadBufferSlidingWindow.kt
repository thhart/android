/*
 * Copyright (C) 2018 The Android Open Source Project
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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.android.tools.idea.diagnostics.hprof.util

import com.android.tools.idea.diagnostics.hprof.parser.HProfEventBasedParser
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.currentJavaVersion
import sun.misc.Unsafe
import sun.nio.ch.DirectBuffer
import java.nio.ByteBuffer
import java.nio.channels.FileChannel


class HProfReadBufferSlidingWindow(private val channel: FileChannel, parser: HProfEventBasedParser) :
  AbstractHProfNavigatorReadBuffer(parser) {
  private val bufferSize = 10_000_000L
  private val size = channel.size()

  private var buffer: ByteBuffer
  private var bufferOffset = 0L

  companion object {
    private val LOG = Logger.getInstance(HProfReadBufferSlidingWindow::class.java)
  }

  init {
    buffer = channel.map(FileChannel.MapMode.READ_ONLY, bufferOffset, Math.min(bufferSize, size))
  }

  override fun close() {
    invokeCleaner(buffer)
  }

  private fun invokeCleaner(byteBuffer: ByteBuffer) {
    try {
      if (currentJavaVersion().feature >= 9) {
        val unsafeClass = Unsafe::class.java
        val invokeCleanerMethod = unsafeClass.getMethod("invokeCleaner", ByteBuffer::class.java)
        val theUnsafeField = unsafeClass.declaredFields.find { f -> f.name == "theUnsafe" }
        theUnsafeField?.let {
          it.isAccessible = true
          val unsafe = it.get(unsafeClass) as Unsafe
          invokeCleanerMethod.invoke(unsafe, byteBuffer)
        }
      }
      else {
        (byteBuffer as? DirectBuffer)?.cleaner()?.clean()
      }
    } catch (t: Throwable) {
      LOG.error(t)
    }
  }

  override fun position(newPosition: Long) {
    if (newPosition >= bufferOffset && newPosition <= bufferOffset + bufferSize) {
      buffer.position((newPosition - bufferOffset).toInt())
    }
    else {
      remapBuffer(newPosition)
    }
  }

  private fun remapBuffer(newPosition: Long) {
    val oldBuffer = buffer

    buffer = channel.map(FileChannel.MapMode.READ_ONLY, newPosition, Math.min(bufferSize, size - newPosition))
    bufferOffset = newPosition

    // Force clean up previous buffer
    invokeCleaner(oldBuffer)
  }

  override fun isEof(): Boolean {
    return position() == size
  }

  override fun position(): Long {
    return bufferOffset + buffer.position()
  }

  override fun get(bytes: ByteArray) {
    if (bytes.size <= buffer.remaining()) {
      buffer.get(bytes)
    }
    else {
      var remaining = bytes.size
      var offset = 0
      while (remaining > 0) {
        remapBuffer(position())
        val bytesToFetch = Math.min(remaining, bufferSize.toInt())
        buffer.get(bytes, offset, bytesToFetch)
        remaining -= bytesToFetch
        offset += bytesToFetch
      }
    }
  }

  override fun getByteBuffer(size: Int): ByteBuffer {
    var useSlice = false
    if (size < buffer.remaining()) {
      useSlice = true
    }
    else if (size < bufferSize) {
      remapBuffer(position())
      useSlice = true
    }
    if (useSlice) {
      val slicedBuffer = buffer.slice()
      slicedBuffer.limit(size)
      skip(size)
      return slicedBuffer.asReadOnlyBuffer()
    }
    else {
      val bytes = ByteArray(size)
      get(bytes)
      return ByteBuffer.wrap(bytes)
    }
  }

  override fun get(): Byte {
    if (buffer.remaining() < 1) {
      remapBuffer(position())
    }
    return buffer.get()
  }

  override fun getShort(): Short {
    if (buffer.remaining() < 2) {
      remapBuffer(position())
    }
    return buffer.short
  }

  override fun getInt(): Int {
    if (buffer.remaining() < 4) {
      remapBuffer(position())
    }
    return buffer.int
  }

  override fun getLong(): Long {
    if (buffer.remaining() < 8) {
      remapBuffer(position())
    }
    return buffer.long
  }

}