package es.zenvairo.nextcloudsync.root

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import es.zenvairo.nextcloudsync.data.auth.NextcloudAccount
import es.zenvairo.nextcloudsync.data.db.entity.VirtualFolderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RootMountController"
private const val MOUNT_TIMEOUT_MILLIS = 20_000L

/**
 * Parent of every virtual folder's mountpoint. A fixed, user-visible location in shared storage
 * rather than the app's private external dir: the mounts are made in init's namespace and propagate
 * everywhere, so a normal file manager can browse them. Anything under Android/data would be
 * off-limits to other apps regardless of the mount.
 */
const val VIRTUAL_MOUNT_ROOT = "/storage/emulated/0/NextcloudVirtual"

sealed class MountResult {
    data class Success(val mountpoint: String) : MountResult()
    data class Failure(val reason: String) : MountResult()
}

/**
 * Starts and stops the root-side FUSE processes, one per virtual folder.
 *
 * Each mount is served by its own `app_process` running [RootMountEntryPoint] as root; that process
 * lives for as long as the mount does (see that file for why the fd isn't handed back here).
 */
@Singleton
class RootMountController @Inject constructor(@ApplicationContext private val context: Context) {

    private val processes = HashMap<Long, Process>()

    fun mountPathFor(folder: VirtualFolderEntity): String =
        File(VIRTUAL_MOUNT_ROOT, sanitize(folder.displayName)).absolutePath

    suspend fun mount(folder: VirtualFolderEntity, account: NextcloudAccount): MountResult =
        withContext(Dispatchers.IO) {
            val mountPath = mountPathFor(folder)
            // Mountpoints are stable across launches, so a mount left behind by a process that died
            // without unmounting would otherwise get a second one stacked on top of it.
            forceUnmount(mountPath)

            try {
                val apkPath = context.applicationInfo.sourceDir
                val entryClass = "es.zenvairo.nextcloudsync.root.RootMountEntryPointKt"
                val uid = android.os.Process.myUid()
                val suCommand = "CLASSPATH=$apkPath app_process / $entryClass \"$mountPath\" $uid $uid"

                val process = ProcessBuilder("su", "-c", suCommand).redirectErrorStream(true).start()

                // The root process reads this before it takes over fd 0 for /dev/fuse. Credentials
                // go over stdin rather than argv so they stay out of the process table.
                process.outputStream.bufferedWriter().apply {
                    write(account.serverUrl); newLine()
                    write(account.username); newLine()
                    write(account.appPassword); newLine()
                    write(folder.remotePath); newLine()
                    write(cacheDirFor(folder).absolutePath); newLine()
                    write(folder.cacheMaxBytes.toString()); newLine()
                    flush()
                }

                when (val outcome = waitForMountOutcome(process, process.inputStream.bufferedReader())) {
                    is MountOutcome.Ok -> {
                        processes[folder.id] = process
                        Log.i(TAG, "mounted ${folder.displayName} at $mountPath")
                        MountResult.Success(mountPath)
                    }
                    is MountOutcome.Failed -> {
                        Log.e(TAG, "mount failed for ${folder.displayName}: ${outcome.output}")
                        MountResult.Failure(outcome.output)
                    }
                    MountOutcome.TimedOut ->
                        MountResult.Failure("timed out waiting for the root process to confirm the mount")
                }
            } catch (e: Exception) {
                Log.e(TAG, "mount failed for ${folder.displayName}", e)
                MountResult.Failure("${e.javaClass.simpleName}: ${e.message}")
            }
        }

    suspend fun unmount(folder: VirtualFolderEntity): MountResult = withContext(Dispatchers.IO) {
        val path = mountPathFor(folder)
        try {
            val result = forceUnmount(path)
            processes.remove(folder.id)?.destroy()
            result
        } catch (e: Exception) {
            Log.e(TAG, "unmount failed for ${folder.displayName}", e)
            MountResult.Failure("${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** True if something is currently mounted at this folder's mountpoint. */
    suspend fun isMounted(folder: VirtualFolderEntity): Boolean = withContext(Dispatchers.IO) {
        val path = mountPathFor(folder)
        runCatching {
            File("/proc/self/mounts").readLines().any { it.contains(" $path ") }
        }.getOrDefault(false)
    }

    fun cacheDirFor(folder: VirtualFolderEntity): File = File(context.cacheDir, "vfs/${folder.id}")

    private sealed class MountOutcome {
        object Ok : MountOutcome()
        data class Failed(val output: String) : MountOutcome()
        object TimedOut : MountOutcome()
    }

    /** Reads the root process's stdout line by line until it sees "MOUNT_OK" or the process exits. */
    private fun waitForMountOutcome(process: Process, reader: BufferedReader): MountOutcome {
        val deadline = System.currentTimeMillis() + MOUNT_TIMEOUT_MILLIS
        val collected = StringBuilder()
        while (System.currentTimeMillis() < deadline) {
            if (reader.ready()) {
                val line = reader.readLine() ?: break
                if (line == "MOUNT_OK") return MountOutcome.Ok
                collected.appendLine(line)
            } else if (!process.isAlive) {
                break
            } else {
                Thread.sleep(50)
            }
        }
        if (!process.isAlive && process.exitValue() != 0) {
            return MountOutcome.Failed(
                collected.toString().ifBlank { "su exited ${process.exitValue()} with no output" }
            )
        }
        return if (System.currentTimeMillis() >= deadline) MountOutcome.TimedOut
        else MountOutcome.Failed(collected.toString().ifBlank { "the root process exited unexpectedly" })
    }

    /** Tears down whatever is mounted at [path], if anything. A "not mounted" umount is not a failure. */
    private fun forceUnmount(path: String): MountResult {
        // Must unmount in the same (init) namespace the mount was made in - see RootMountEntryPoint.
        val proc = ProcessBuilder("su", "-c", "nsenter -t 1 -m -- umount -l \"$path\"")
            .redirectErrorStream(true)
            .start()
        val output = proc.inputStream.bufferedReader().readText()
        val exit = proc.waitFor()
        return if (exit == 0) MountResult.Success(path) else MountResult.Failure("umount exit=$exit: $output")
    }

    /** The display name becomes a real directory name, so keep it to something a path can hold. */
    private fun sanitize(name: String): String =
        name.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_").ifBlank { "folder" }
}
