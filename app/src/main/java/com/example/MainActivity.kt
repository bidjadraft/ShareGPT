package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.db.Conversation
import com.example.data.db.Message
import com.example.data.db.Prompt
import com.example.data.file.ImportJsonResult
import com.example.data.file.JsonShareHelper
import com.example.data.prefs.AppFontSize
import com.example.data.prefs.AppThemeMode
import com.example.ui.screens.ChatListScreen
import com.example.ui.screens.ConversationScreen
import com.example.ui.screens.ImportConversationDialog
import com.example.ui.screens.ImportPromptDialog
import com.example.ui.screens.PromptsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.PromptChatTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var incomingUriState = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as PromptChatApp
        val container = app.container

        handleIntent(intent)

        setContent {
            val themeMode by container.userPreferencesRepository.themeModeFlow.collectAsState(initial = AppThemeMode.WARM)
            val fontSize by container.userPreferencesRepository.fontSizeFlow.collectAsState(initial = AppFontSize.MEDIUM)

            PromptChatTheme(themeMode = themeMode, fontSize = fontSize) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    PromptChatAppRoot(
                        container = container,
                        incomingUri = incomingUriState.value,
                        onClearIncomingUri = { incomingUriState.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action == Intent.ACTION_VIEW || action == Intent.ACTION_SEND) {
            val uri: Uri? = intent.data ?: (intent.getParcelableExtra(Intent.EXTRA_STREAM))
            if (uri != null) {
                incomingUriState.value = uri
            }
        }
    }
}

// Order: Settings (Left), Prompts (Center), Chats (Right)
sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
) {
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    object Prompts : Screen("prompts", "Prompts", Icons.AutoMirrored.Filled.FormatListBulleted, Icons.AutoMirrored.Outlined.FormatListBulleted)
    object Chats : Screen("chats", "Chats", Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat)
}

@Composable
fun PromptChatAppRoot(
    container: com.example.di.AppContainer,
    incomingUri: Uri?,
    onClearIncomingUri: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Import states
    var importResult by remember { mutableStateOf<ImportJsonResult?>(null) }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(incomingUri) {
        if (incomingUri != null) {
            val result = JsonShareHelper.parseJsonFromUri(context, incomingUri)
            if (result is ImportJsonResult.Error) {
                importErrorMessage = result.message
            } else {
                importResult = result
            }
            onClearIncomingUri()
        }
    }

    // Bottom bar screens: Settings (left) and Chats (right)
    val bottomBarScreens = listOf(Screen.Settings, Screen.Chats)
    val showBottomBar = currentRoute in bottomBarScreens.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    modifier = Modifier.testTag("bottom_navigation_bar"),
                    tonalElevation = 0.dp,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    bottomBarScreens.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            selected = isSelected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ),
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            modifier = Modifier.testTag("nav_tab_${screen.route}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Chats.route,
                enterTransition = {
                    val target = targetState.destination.route
                    val direction = if (target == Screen.Settings.route) {
                        AnimatedContentTransitionScope.SlideDirection.Right
                    } else {
                        AnimatedContentTransitionScope.SlideDirection.Left
                    }
                    fadeIn(animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                        slideIntoContainer(direction, animationSpec = tween(280, easing = FastOutSlowInEasing))
                },
                exitTransition = {
                    val target = targetState.destination.route
                    val direction = if (target == Screen.Settings.route) {
                        AnimatedContentTransitionScope.SlideDirection.Right
                    } else {
                        AnimatedContentTransitionScope.SlideDirection.Left
                    }
                    fadeOut(animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                        slideOutOfContainer(direction, animationSpec = tween(280, easing = FastOutSlowInEasing))
                },
                popEnterTransition = {
                    val target = targetState.destination.route
                    val direction = if (target == Screen.Settings.route) {
                        AnimatedContentTransitionScope.SlideDirection.Right
                    } else {
                        AnimatedContentTransitionScope.SlideDirection.Left
                    }
                    fadeIn(animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                        slideIntoContainer(direction, animationSpec = tween(280, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    val target = targetState.destination.route
                    val direction = if (target == Screen.Settings.route) {
                        AnimatedContentTransitionScope.SlideDirection.Right
                    } else {
                        AnimatedContentTransitionScope.SlideDirection.Left
                    }
                    fadeOut(animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                        slideOutOfContainer(direction, animationSpec = tween(280, easing = FastOutSlowInEasing))
                }
            ) {
                composable(Screen.Settings.route) {
                    SettingsScreen(container = container)
                }

                composable(Screen.Prompts.route) {
                    PromptsScreen(
                        container = container,
                        onOpenConversation = { id ->
                            navController.navigate("conversation/$id")
                        }
                    )
                }

                composable(Screen.Chats.route) {
                    ChatListScreen(
                        container = container,
                        onOpenConversation = { id ->
                            navController.navigate("conversation/$id")
                        },
                        onNavigateToPrompts = {
                            navController.navigate(Screen.Prompts.route)
                        }
                    )
                }

                composable(
                    route = "conversation/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { backStackEntry ->
                    val conversationId = backStackEntry.arguments?.getLong("id") ?: 0L
                    BackHandler {
                        navController.popBackStack()
                    }
                    ConversationScreen(
                        conversationId = conversationId,
                        container = container,
                        onBack = { navController.popBackStack() },
                        onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                    )
                }
            }
        }
    }

    // Import Prompt Dialog
    val currentImport = importResult
    if (currentImport is ImportJsonResult.PromptImport) {
        ImportPromptDialog(
            promptData = currentImport,
            onDismiss = { importResult = null },
            onConfirm = {
                coroutineScope.launch {
                    val existing = container.database.promptDao().getPromptByTitle(currentImport.title)
                    val resolvedTitle = if (existing != null) "${currentImport.title} (imported)" else currentImport.title
                    val newPrompt = Prompt(
                        title = resolvedTitle,
                        content = currentImport.content,
                        icon = currentImport.icon,
                        allowedFileTypes = currentImport.allowedFileTypes,
                        autoExecuteOnFile = currentImport.autoExecuteOnFile,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    container.database.promptDao().insertPrompt(newPrompt)
                    importResult = null
                    Toast.makeText(context, "Prompt added: $resolvedTitle", Toast.LENGTH_SHORT).show()
                    navController.navigate(Screen.Prompts.route)
                }
            }
        )
    }

    // Import Conversation Dialog
    if (currentImport is ImportJsonResult.ConversationImport) {
        ImportConversationDialog(
            conversationData = currentImport,
            onDismiss = { importResult = null },
            onConfirm = {
                coroutineScope.launch {
                    val existingPrompt = container.database.promptDao().getPromptByTitle(currentImport.promptTitle)
                    val promptId = if (existingPrompt != null) {
                        existingPrompt.id
                    } else {
                        val newPrompt = Prompt(
                            title = currentImport.promptTitle,
                            content = currentImport.promptContent,
                            icon = currentImport.promptIcon,
                            allowedFileTypes = "chat,doc,pdf,image,audio",
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                        container.database.promptDao().insertPrompt(newPrompt)
                    }

                    val newConv = Conversation(
                        promptId = promptId,
                        title = currentImport.conversationTitle,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        lastMessageAt = System.currentTimeMillis()
                    )
                    val convId = container.database.conversationDao().insertConversation(newConv)

                    for (msgDraft in currentImport.messages) {
                        container.database.messageDao().insertMessage(
                            Message(
                                conversationId = convId,
                                role = msgDraft.role,
                                content = msgDraft.content,
                                attachmentName = msgDraft.attachmentName,
                                attachmentMimeType = msgDraft.attachmentMimeType,
                                createdAt = msgDraft.createdAt
                            )
                        )
                    }

                    importResult = null
                    Toast.makeText(context, "Conversation imported", Toast.LENGTH_SHORT).show()
                    navController.navigate("conversation/$convId")
                }
            }
        )
    }

    // Error Dialog for invalid import
    importErrorMessage?.let { err ->
        AlertDialog(
            onDismissRequest = { importErrorMessage = null },
            title = { Text("Import Error") },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = { importErrorMessage = null }) {
                    Text("OK")
                }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }
}
