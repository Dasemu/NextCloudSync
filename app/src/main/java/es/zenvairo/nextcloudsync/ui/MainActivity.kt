package es.zenvairo.nextcloudsync.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import es.zenvairo.nextcloudsync.data.auth.AccountRepository
import es.zenvairo.nextcloudsync.ui.conflicts.ConflictsScreen
import es.zenvairo.nextcloudsync.ui.foldereditor.FolderEditorScreen
import es.zenvairo.nextcloudsync.ui.folders.FoldersScreen
import es.zenvairo.nextcloudsync.ui.health.HealthScreen
import es.zenvairo.nextcloudsync.ui.log.LogScreen
import es.zenvairo.nextcloudsync.ui.login.LoginScreen
import es.zenvairo.nextcloudsync.ui.settings.SettingsScreen
import es.zenvairo.nextcloudsync.ui.theme.NextCloudSyncTheme
import es.zenvairo.nextcloudsync.ui.virtualfolders.VirtualFolderEditorScreen
import es.zenvairo.nextcloudsync.ui.virtualfolders.VirtualFoldersScreen
import javax.inject.Inject

object Routes {
    const val LOGIN = "login"
    const val FOLDERS = "folders"
    const val FOLDER_EDITOR = "folder_editor?id={id}"
    const val SETTINGS = "settings"
    const val HEALTH = "health"
    const val CONFLICTS = "conflicts"
    const val LOG = "log"
    const val VIRTUAL_FOLDERS = "virtual_folders"
    const val VIRTUAL_FOLDER_EDITOR = "virtual_folder_editor?id={id}"

    fun folderEditor(id: Long? = null) = "folder_editor?id=${id ?: -1}"
    fun virtualFolderEditor(id: Long? = null) = "virtual_folder_editor?id=${id ?: -1}"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var accountRepository: AccountRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val start = if (accountRepository.account.value != null) Routes.FOLDERS else Routes.LOGIN
        setContent {
            NextCloudSyncTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NcSyncNavHost(rememberNavController(), start)
                }
            }
        }
    }
}

@Composable
private fun NcSyncNavHost(navController: NavHostController, startDestination: String) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = {
                navController.navigate(Routes.FOLDERS) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            })
        }
        composable(Routes.FOLDERS) {
            FoldersScreen(
                onAddFolder = { navController.navigate(Routes.folderEditor()) },
                onEditFolder = { id -> navController.navigate(Routes.folderEditor(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenHealth = { navController.navigate(Routes.HEALTH) },
                onOpenConflicts = { navController.navigate(Routes.CONFLICTS) },
                onOpenLog = { navController.navigate(Routes.LOG) },
                onOpenVirtualFolders = { navController.navigate(Routes.VIRTUAL_FOLDERS) }
            )
        }
        composable(Routes.VIRTUAL_FOLDERS) {
            VirtualFoldersScreen(
                onBack = { navController.popBackStack() },
                onAddFolder = { navController.navigate(Routes.virtualFolderEditor()) },
                onEditFolder = { id -> navController.navigate(Routes.virtualFolderEditor(id)) }
            )
        }
        composable(Routes.VIRTUAL_FOLDER_EDITOR) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")?.toLongOrNull()?.takeIf { it >= 0 }
            VirtualFolderEditorScreen(folderId = id, onDone = { navController.popBackStack() })
        }
        composable(Routes.FOLDER_EDITOR) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")?.toLongOrNull()?.takeIf { it >= 0 }
            FolderEditorScreen(folderId = id, onDone = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onSignedOut = { navController.navigate(Routes.LOGIN) { popUpTo(0) } },
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.FOLDERS) { popUpTo(0) }
                    }
                }
            )
        }
        composable(Routes.HEALTH) { HealthScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.CONFLICTS) { ConflictsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.LOG) { LogScreen(onBack = { navController.popBackStack() }) }
    }
}
