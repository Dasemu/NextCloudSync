package es.zenvairo.nextcloudsync.root

import android.system.Os
import android.system.OsConstants
import es.zenvairo.nextcloudsync.data.remote.WebDavClient
import es.zenvairo.nextcloudsync.fuse.HydrationCache
import es.zenvairo.nextcloudsync.fuse.VirtualFileTree
import es.zenvairo.nextcloudsync.fuse.VirtualFuseServer
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Entry point for a standalone process launched as `app_process ... RootMountEntryPointKt`,
 * itself invoked via `su -c` so it runs as root - this is NOT part of the normal app lifecycle
 * (no Application.onCreate, no Context). It opens /dev/fuse, hands that fd to `toybox mount` (a
 * subprocess whose stdin - our already-open /dev/fuse fd, via dup2 - it inherits) to perform the
 * actual mount(2) syscall (android.system.Os has no public mount() binding), then serves the FUSE
 * protocol itself for as long as the mount stays alive.
 *
 * Earlier iterations tried handing the fd back to the unprivileged app process over a LocalSocket
 * (android.net.LocalSocket ancillary fd passing) so the root process could exit immediately. That
 * consistently failed with `IOException: Message too long` from `LocalSocketImpl.readba_native`
 * on this device/ROM regardless of read buffer size or write() overload used - underlying
 * recvmsg()/cmsg handling that couldn't be fixed from the Java side. Serving directly from this
 * already-root process sidesteps the whole ancillary-fd code path; the trade-off is this process
 * now lives for as long as the mount is active instead of handing off and exiting immediately.
 *
 * Args: <mountpoint> <uid> <gid>
 */
fun main(args: Array<String>) {
    if (args.size != 3) {
        System.err.println("usage: RootMountEntryPoint <mountpoint> <uid> <gid>")
        kotlin.system.exitProcess(2)
    }
    val mountpoint = args[0]
    val uid = args[1].toInt()
    val gid = args[2].toInt()

    // Read the config off stdin before /dev/fuse takes over fd 0. Credentials go this way rather
    // than as argv so they never show up in `ps` output.
    val config = try {
        MountConfig.readFrom(System.`in`)
    } catch (e: Exception) {
        System.err.println("reading config from stdin failed: $e")
        kotlin.system.exitProcess(6)
    }

    val fuseFd: FileDescriptor = try {
        Os.open("/dev/fuse", OsConstants.O_RDWR, 0)
    } catch (e: Exception) {
        System.err.println("open(/dev/fuse) failed: $e")
        kotlin.system.exitProcess(3)
    }

    try {
        Os.dup2(fuseFd, 0)
    } catch (e: Exception) {
        System.err.println("dup2 failed: $e")
        kotlin.system.exitProcess(4)
    }

    ProcessBuilder("toybox", "mkdir", "-p", mountpoint).redirectErrorStream(true).start().waitFor()

    val mountOptions = "rw,nosuid,nodev,noexec,relatime,user_id=$uid,group_id=$gid," +
        "rootmode=40755,fd=0,allow_other,default_permissions,max_read=131072," +
        "context=u:object_r:fuse:s0"
    // nsenter into init's mount namespace: mounting from our own (the app's) namespace would make
    // the mount visible only to this app. /storage/emulated is a shared-propagation subtree, so a
    // mount placed under it from init's namespace propagates into every app's namespace, including
    // apps already running. The /dev/fuse fd stays valid across the namespace switch - open file
    // descriptors are unaffected by CLONE_NEWNS - and is inherited through the exec as stdin.
    val mountProcess = ProcessBuilder(
        "nsenter", "-t", "1", "-m", "--",
        "toybox", "mount", "-t", "fuse", "-o", mountOptions, "none", mountpoint
    )
        .redirectInput(ProcessBuilder.Redirect.INHERIT)
        .redirectErrorStream(true)
        .start()
    val mountOutput = mountProcess.inputStream.bufferedReader().readText()
    val mountExit = mountProcess.waitFor()
    if (mountExit != 0) {
        System.err.println("mount failed (exit=$mountExit): $mountOutput")
        kotlin.system.exitProcess(5)
    }

    // Signals success to RootMountController, which watches this process's stdout for this line.
    println("MOUNT_OK")
    System.out.flush()

    val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .build()
    val webDav = WebDavClient(okHttpClient, config.serverUrl, config.username, config.appPassword)
    val tree = VirtualFileTree(webDav, config.remotePath)
    val cache = HydrationCache(webDav, File(config.cacheDir), config.cacheMaxBytes)

    VirtualFuseServer(fuseFd, uid, gid, tree, cache).loop()
    kotlin.system.exitProcess(0)
}

/** Everything the root process needs that can't be derived without a Context. */
data class MountConfig(
    val serverUrl: String,
    val username: String,
    val appPassword: String,
    val remotePath: String,
    val cacheDir: String,
    val cacheMaxBytes: Long
) {
    companion object {
        /**
         * Reads exactly six newline-terminated fields, one byte at a time: a buffered reader could
         * swallow bytes past the config, and fd 0 is about to be reused for /dev/fuse.
         */
        fun readFrom(input: InputStream): MountConfig {
            val fields = (1..6).map { readLine(input) }
            return MountConfig(
                serverUrl = fields[0],
                username = fields[1],
                appPassword = fields[2],
                remotePath = fields[3],
                cacheDir = fields[4],
                cacheMaxBytes = fields[5].toLong()
            )
        }

        private fun readLine(input: InputStream): String {
            val out = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b == -1) throw IOException("unexpected end of config on stdin")
                if (b == '\n'.code) return out.toString(Charsets.UTF_8.name())
                out.write(b)
            }
        }
    }
}
