package com.assayflux.pockethost.worker

import android.content.Context
import android.os.Process
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.io.IOException
import java.nio.channels.FileLockInterruptionException
import java.nio.channels.OverlappingFileLockException

/** File-backed IPC state shared safely between the main process and the isolated :node process. */
object NodeRuntimeStateStore {
    private const val LOCK_WAIT_NANOS = 5_000_000_000L

    private fun file(context: Context) = File(context.noBackupFilesDir, "node-runtime-state.json")

    @Synchronized
    fun update(
        context: Context,
        state: String? = null,
        detail: String? = null,
        projectRoot: String? = null,
        metrics: JSONObject? = null
    ) {
        val target = file(context)
        target.parentFile?.mkdirs()
        val parent = target.parentFile ?: return
        val lockFile = File(parent, target.name + ".lock")

        // This store is shared by the app process and :node. FileChannel.lock() is
        // interruptible; an Android service shutdown can interrupt the metrics thread
        // while it waits, which otherwise crashes the entire :node process.
        runCatching {
            RandomAccessFile(lockFile, "rw").channel.use { channel ->
                val deadline = System.nanoTime() + LOCK_WAIT_NANOS
                while (System.nanoTime() < deadline) {
                    if (Thread.currentThread().isInterrupted) return@runCatching
                    val lock = try {
                        channel.tryLock()
                    } catch (_: OverlappingFileLockException) {
                        null
                    } catch (_: FileLockInterruptionException) {
                        Thread.currentThread().interrupt()
                        return@runCatching
                    } catch (_: IOException) {
                        return@runCatching
                    }

                    if (lock != null) {
                        lock.use {
                            val current = readObject(target)
                            if (state != null) current.put("state", state)
                            if (detail != null) current.put("detail", detail)
                            if (projectRoot != null) current.put("projectRoot", projectRoot)
                            if (metrics != null) current.put("metrics", metrics)
                            current.put("updatedAt", System.currentTimeMillis())
                            val temporary = File(parent, target.name + "." + Process.myPid() + ".tmp")
                            temporary.writeText(current.toString(), Charsets.UTF_8)
                            if (!temporary.renameTo(target)) {
                                temporary.copyTo(target, overwrite = true)
                                temporary.delete()
                            }
                        }
                        return@runCatching
                    }

                    try {
                        Thread.sleep(20)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return@runCatching
                    }
                }
            }
        }
    }

    fun state(context: Context): String = read(context).optString("state", "STOPPED")
    fun metrics(context: Context): JSONObject? = read(context).optJSONObject("metrics")
    fun projectRoot(context: Context): String? = read(context).optString("projectRoot").takeIf { it.isNotBlank() }
    fun detail(context: Context): String = read(context).optString("detail")

    private fun read(context: Context): JSONObject = readObject(file(context))

    private fun readObject(file: File): JSONObject =
        runCatching { if (file.isFile) JSONObject(file.readText(Charsets.UTF_8)) else JSONObject() }
            .getOrDefault(JSONObject())
}
