package es.zenvairo.nextcloudsync.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

@Serializable
data class LoginFlowInit(val poll: PollInfo, val login: String)

@Serializable
data class PollInfo(val token: String, val endpoint: String)

@Serializable
data class LoginFlowCredentials(val server: String, val loginName: String, val appPassword: String)

sealed class LoginPollResult {
    data class Success(val credentials: LoginFlowCredentials) : LoginPollResult()
    object Pending : LoginPollResult()
    object TimedOut : LoginPollResult()
    data class Error(val cause: Throwable) : LoginPollResult()
}

/**
 * Implements Nextcloud's Login Flow v2: https://docs.nextcloud.com/server/latest/developer_manual/client_apis/LoginFlow/index.html
 * The user authenticates (with 2FA/SSO if configured) in a browser tab; this app never sees the
 * real password, only the resulting app password.
 */
class LoginFlowV2Client @Inject constructor(private val okHttpClient: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun initiate(serverUrl: String): LoginFlowInit = withContext(Dispatchers.IO) {
        val url = normalize(serverUrl) + "/index.php/login/v2"
        val request = Request.Builder().url(url).post("".toRequestBody(null)).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Login init failed: HTTP ${response.code}")
            val body = response.body?.string() ?: error("Empty login init response")
            json.decodeFromString(LoginFlowInit.serializer(), body)
        }
    }

    /** Polls the endpoint once. Callers loop this with a delay until Success/TimedOut. */
    suspend fun poll(poll: PollInfo): LoginPollResult = withContext(Dispatchers.IO) {
        val formBody = "token=${poll.token}".toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val request = Request.Builder().url(poll.endpoint).post(formBody).build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> LoginPollResult.Pending
                    response.isSuccessful -> {
                        val body = response.body?.string() ?: return@use LoginPollResult.Pending
                        LoginPollResult.Success(json.decodeFromString(LoginFlowCredentials.serializer(), body))
                    }
                    else -> LoginPollResult.Pending
                }
            }
        } catch (t: Throwable) {
            LoginPollResult.Error(t)
        }
    }

    /** Polls until success, timeout, or a hard error. 3s interval as recommended by Nextcloud docs. */
    suspend fun pollUntilComplete(
        poll: PollInfo,
        intervalMillis: Long = 3000,
        timeoutMillis: Long = 20 * 60 * 1000
    ): LoginPollResult {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            when (val result = poll(poll)) {
                is LoginPollResult.Success -> return result
                is LoginPollResult.Error -> return result
                else -> delay(intervalMillis)
            }
        }
        return LoginPollResult.TimedOut
    }

    private fun normalize(serverUrl: String): String {
        var url = serverUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        return url.trimEnd('/')
    }
}
