// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import com.intellij.util.lang.HashMapZipFile
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import okio.IOException
import org.jetbrains.intellij.build.forEachConcurrent
import org.jetbrains.intellij.build.http2Client.Http2ClientConnection
import org.jetbrains.intellij.build.http2Client.Http2ClientConnectionFactory
import org.jetbrains.intellij.build.http2Client.ZstdDecompressContextPool
import org.jetbrains.intellij.build.http2Client.checkMirrorAndConnect
import org.jetbrains.intellij.build.http2Client.download
import org.jetbrains.intellij.build.io.INDEX_FILENAME
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.EnumSet
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.name
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

internal val downloadTimeout: Duration = Integer.getInteger("intellij.build.compilation.download.timeout.minutes", 10).minutes

internal suspend fun downloadCompilationCache(
  serverUrl: URI,
  client: Http2ClientConnectionFactory,
  toDownload: Collection<FetchAndUnpackItem>,
  downloadedBytes: AtomicLong,
  skipUnpack: Boolean,
  saveHash: Boolean,
) {
  checkMirrorAndConnect(initialServerUri = serverUrl, client = client) { connection, urlPathPrefix ->
    val zstdDecompressContextPool = ZstdDecompressContextPool()
    toDownload.forEachConcurrent(downloadParallelism) { item ->
      val urlPath = "$urlPathPrefix/${item.name}/${item.file.fileName}"
      spanBuilder("download").setAttribute("name", item.name).setAttribute("urlPath", urlPath).use { span ->
        try {
          val downloadedSize = doDownloadWithTimeout(
            item = item,
            urlPath = urlPath,
            skipUnpack = skipUnpack,
            saveHash = saveHash,
            connection = connection,
            zstdDecompressContextPool = zstdDecompressContextPool,
            span = span,
          )
          downloadedBytes.getAndAdd(downloadedSize)
        }
        catch (e: CancellationException) {
          if (coroutineContext.isActive) {
            // well, we are not canceled, only child
            throw IllegalStateException("Unexpected cancellation - action is cancelled itself", e)
          }
        }
        catch (e: Throwable) {
          span.recordException(e)
          throw CompilePartDownloadFailedError(item, e)
        }
      }
    }
  }
}

private suspend fun doDownloadWithTimeout(
  item: FetchAndUnpackItem,
  urlPath: String,
  skipUnpack: Boolean,
  saveHash: Boolean,
  connection: Http2ClientConnection,
  zstdDecompressContextPool: ZstdDecompressContextPool,
  span: Span,
): Long {
  var attempt = 0
  while (true) {
    try {
      return withTimeout(downloadTimeout) {
        download(
          item = item,
          urlPath = urlPath,
          skipUnpack = skipUnpack,
          saveHash = saveHash,
          connection = connection,
          zstdDecompressContextPool = zstdDecompressContextPool,
        )
      }
    }
    catch (e: TimeoutCancellationException) {
      span.addEvent("download timed out ($downloadTimeout), attempt $attempt")
      if (attempt >= 3) {
        throw IllegalStateException("Cannot download", e)
      }
    }

    attempt++
  }
}

private suspend fun download(
  item: FetchAndUnpackItem,
  urlPath: CharSequence,
  skipUnpack: Boolean,
  saveHash: Boolean,
  connection: Http2ClientConnection,
  zstdDecompressContextPool: ZstdDecompressContextPool,
): Long {
  val (downloaded, digest) = connection.download(path = urlPath, file = item.file, zstdDecompressContextPool = zstdDecompressContextPool, digestFactory = { sha256() })
  val computedHash = digestToString(digest!!)
  if (computedHash != item.hash) {
    //println("actualHash  : ${computeHash(item.file)}")
    //println("expectedHash: ${item.hash}")
    //println("computedHash: $computedHash")

    val spanAttributes = Attributes.of(
      AttributeKey.stringKey("name"), item.file.name,
      AttributeKey.stringKey("expected"), item.hash,
      AttributeKey.stringKey("computed"), computedHash,
    )
    throw HashMismatchException("hash mismatch ($spanAttributes)")
  }

  if (!skipUnpack) {
    spanBuilder("unpack").setAttribute("name", item.name).use {
      unpackCompilationPartArchive(item, saveHash)
    }
  }
  return downloaded
}

internal class CompilePartDownloadFailedError(@JvmField val item: FetchAndUnpackItem, cause: Throwable) : RuntimeException(cause) {
  override fun toString(): String = "item: $item, error: ${super.toString()}"
}

internal class HashMismatchException(message: String) : IOException(message)

internal fun unpackCompilationPartArchive(item: FetchAndUnpackItem, saveHash: Boolean) {
  unpackTrustedArchive(archiveFile = item.file, outDir = item.output)
  if (saveHash) {
    // save actual hash
    Files.writeString(item.output.resolve(".hash"), item.hash)
  }
}

private val OVERWRITE_OPERATION = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

private fun unpackTrustedArchive(archiveFile: Path, outDir: Path) {
  Files.createDirectories(outDir)
  HashMapZipFile.load(archiveFile).use { zipFile ->
    val createdDirs = HashSet<Path>()
    createdDirs.add(outDir)
    for (entry in zipFile.entries) {
      if (entry.isDirectory || entry.name == INDEX_FILENAME) {
        continue
      }

      val file = outDir.resolve(entry.name)
      val parent = file.parent
      if (createdDirs.add(parent)) {
        Files.createDirectories(parent)
      }

      FileChannel.open(file, OVERWRITE_OPERATION).use { channel ->
        channel.write(entry.getByteBuffer(zipFile, null))
      }
    }
  }
}