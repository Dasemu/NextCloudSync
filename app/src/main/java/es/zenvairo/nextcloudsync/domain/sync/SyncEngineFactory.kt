package es.zenvairo.nextcloudsync.domain.sync

import es.zenvairo.nextcloudsync.data.auth.AccountRepository
import es.zenvairo.nextcloudsync.data.db.dao.ConflictDao
import es.zenvairo.nextcloudsync.data.db.dao.FileStateDao
import es.zenvairo.nextcloudsync.data.db.dao.SyncEventDao
import es.zenvairo.nextcloudsync.data.remote.ChunkedUploader
import es.zenvairo.nextcloudsync.data.remote.WebDavClient
import okhttp3.Credentials
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/** Builds a [SyncEngine] bound to whichever Nextcloud account is currently signed in. */
@Singleton
class SyncEngineFactory @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val accountRepository: AccountRepository,
    private val fileStateDao: FileStateDao,
    private val conflictDao: ConflictDao,
    private val syncEventDao: SyncEventDao
) {
    fun create(): SyncEngine? {
        val account = accountRepository.account.value ?: return null
        val webDavClient = WebDavClient(okHttpClient, account.serverUrl, account.username, account.appPassword)
        val chunkedUploader = ChunkedUploader(
            webDavClient = webDavClient,
            serverUrl = account.serverUrl,
            username = account.username,
            authorizationHeader = Credentials.basic(account.username, account.appPassword)
        )
        return SyncEngine(webDavClient, chunkedUploader, fileStateDao, conflictDao, syncEventDao)
    }
}
