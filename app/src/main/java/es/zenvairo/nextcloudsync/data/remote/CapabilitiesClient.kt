package es.zenvairo.nextcloudsync.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** Reads the OCS capabilities endpoint to see whether the server has the notify_push app,
 *  which lets us open a push websocket instead of polling for remote changes. */
class CapabilitiesClient(
    private val okHttpClient: OkHttpClient,
    private val serverUrl: String,
    private val authorizationHeader: String
) {
    suspend fun fetchNotifyPushWebSocketUrl(): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(serverUrl.trimEnd('/') + "/ocs/v2.php/cloud/capabilities?format=json")
            .header("Authorization", authorizationHeader)
            .header("OCS-APIRequest", "true")
            .build()
        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                val json = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
                json["ocs"]?.jsonObject
                    ?.get("data")?.jsonObject
                    ?.get("capabilities")?.jsonObject
                    ?.get("notify_push")?.jsonObject
                    ?.get("websocket")?.jsonPrimitive?.content
            }
        }.getOrNull()
    }
}
