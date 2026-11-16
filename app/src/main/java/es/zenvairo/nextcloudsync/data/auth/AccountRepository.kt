package es.zenvairo.nextcloudsync.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class NextcloudAccount(val serverUrl: String, val username: String, val appPassword: String)

@Singleton
class AccountRepository @Inject constructor(@ApplicationContext context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "nextcloud_account",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _account = MutableStateFlow(loadFromPrefs())
    val account: StateFlow<NextcloudAccount?> = _account

    fun save(credentials: LoginFlowCredentials) {
        prefs.edit()
            .putString(KEY_SERVER, credentials.server)
            .putString(KEY_USERNAME, credentials.loginName)
            .putString(KEY_APP_PASSWORD, credentials.appPassword)
            .apply()
        _account.value = NextcloudAccount(credentials.server, credentials.loginName, credentials.appPassword)
    }

    fun signOut() {
        prefs.edit().clear().apply()
        _account.value = null
    }

    private fun loadFromPrefs(): NextcloudAccount? {
        val server = prefs.getString(KEY_SERVER, null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val password = prefs.getString(KEY_APP_PASSWORD, null) ?: return null
        return NextcloudAccount(server, username, password)
    }

    companion object {
        private const val KEY_SERVER = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_APP_PASSWORD = "app_password"
    }
}
