package es.zenvairo.nextcloudsync.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether this app currently holds root.
 *
 * Probed on demand rather than at startup: on KernelSU the first `su` from an app that isn't yet
 * allowlisted just fails, and on a Magisk-style manager it would raise a permission dialog - neither
 * belongs in a background path the user didn't ask for. The result is cached so entering the virtual
 * folders screen doesn't re-run it, and [invalidate] drops the cache after the user has had a chance
 * to grant root in their manager.
 */
@Singleton
class RootAvailability @Inject constructor() {

    @Volatile private var cached: Boolean? = null

    suspend fun isAvailable(): Boolean = cached ?: withContext(Dispatchers.IO) {
        probe().also { cached = it }
    }

    fun invalidate() {
        cached = null
    }

    private fun probe(): Boolean = try {
        val process = ProcessBuilder("su", "-c", "id -u").redirectErrorStream(true).start()
        if (!process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            false
        } else {
            process.inputStream.bufferedReader().readText().trim() == "0"
        }
    } catch (e: Exception) {
        // No su binary at all, or the exec was denied outright.
        false
    }

    private companion object {
        const val PROBE_TIMEOUT_SECONDS = 10L
    }
}
