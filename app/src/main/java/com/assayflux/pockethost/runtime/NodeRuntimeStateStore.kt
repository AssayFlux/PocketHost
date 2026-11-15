package com.assayflux.pockethost.runtime

import android.content.Context
import android.os.Process
import org.json.JSONObject
import java.io.File

/** File-backed IPC state shared safely between the main process and the isolated :node process. */
object NodeRuntimeStateStore {
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
        val current = readObject(target)
        if (state != null) current.put("state", state)
        if (detail != null) current.put("detail", detail)
        if (projectRoot != null) current.put("projectRoot", projectRoot)
        if (metrics != null) current.put("metrics", metrics)
        current.put("updatedAt", System.currentTimeMillis())
        val temporary = File(target.parentFile, target.name + "." + Process.myPid() + ".tmp")
        temporary.writeText(current.toString(), Charsets.UTF_8)
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
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
