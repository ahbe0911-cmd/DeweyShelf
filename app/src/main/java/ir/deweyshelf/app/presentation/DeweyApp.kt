package ir.deweyshelf.app.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.deweyshelf.app.R
import ir.deweyshelf.app.core.toPersianNumber
import ir.deweyshelf.app.presentation.components.DeweyLogo
import ir.deweyshelf.app.presentation.components.EmptyState
import ir.deweyshelf.app.presentation.components.LoadingState
import ir.deweyshelf.app.presentation.screens.BookEditorScreen
import ir.deweyshelf.app.presentation.screens.BooksScreen
import ir.deweyshelf.app.presentation.screens.DataScreen
import ir.deweyshelf.app.presentation.screens.GuideScreen
import ir.deweyshelf.app.presentation.screens.HomeScreen
import ir.deweyshelf.app.presentation.screens.ShelfScreen
import kotlinx.coroutines.launch

private object Routes {
    const val Home = "home"
    const val Books = "books"
    const val Shelf = "shelf"
    const val Guide = "guide"
    const val Data = "data"
    const val Editor = "editor/{bookId}"
    fun editor(bookId: Long = 0) = "editor/$bookId"
}

private data class MainDestination(
    val route: String,
    val label: Int,
    val icon: ImageVector,
)

private val mainDestinations = listOf(
    MainDestination(Routes.Home, R.string.nav_home, Icons.Outlined.Home),
    MainDestination(Routes.Books, R.string.nav_books, Icons.Outlined.LibraryBooks),
    MainDestination(Routes.Shelf, R.string.nav_shelf, Icons.Outlined.Sort),
    MainDestination(Routes.Guide, R.string.nav_guide, Icons.Outlined.MenuBook),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeweyApp(viewModel: DeweyViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route ?: Routes.Home
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isMainRoute = mainDestinations.any { it.route == route }
    val showFab = route == Routes.Books || route == Routes.Shelf

    fun navigateMain(target: String) {
        navController.navigate(target) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun copy(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        notify(context.getString(R.string.copied))
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AppEvent.BookSaved -> snackbarHostState.showSnackbar(context.getString(R.string.book_saved))
                AppEvent.BookUpdated -> snackbarHostState.showSnackbar(context.getString(R.string.book_updated))
                is AppEvent.BookDeleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.book_deleted),
                        actionLabel = context.getString(R.string.undo),
                        duration = SnackbarDuration.Long,
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.restoreBook(event.book)
                }
                is AppEvent.AllDeleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.all_deleted),
                        actionLabel = context.getString(R.string.undo),
                        duration = SnackbarDuration.Long,
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.restoreAll(event.books)
                }
                AppEvent.ImportSucceeded -> snackbarHostState.showSnackbar(context.getString(R.string.import_success))
                AppEvent.OperationFailed -> snackbarHostState.showSnackbar(context.getString(R.string.operation_failed))
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    if (isMainRoute) {
                        DeweyLogo()
                    } else {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.navigate_up),
                            )
                        }
                    }
                },
                title = {
                    if (isMainRoute) {
                        Column {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.app_tagline),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            stringResource(
                                when {
                                    route == Routes.Data -> R.string.data_title
                                    route.startsWith("editor") && backStackEntry?.arguments?.getLong("bookId") != 0L -> R.string.edit_book
                                    else -> R.string.add_book
                                },
                            ),
                        )
                    }
                },
                actions = {
                    if (isMainRoute) {
                        IconButton(onClick = { navController.navigate(Routes.Data) }) {
                            Icon(Icons.Outlined.Storage, contentDescription = stringResource(R.string.menu_data))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (isMainRoute) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    mainDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = route == destination.route,
                            onClick = { navigateMain(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(onClick = { navController.navigate(Routes.editor()) }) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.add_book))
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Home,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable(Routes.Home) {
                HomeScreen(
                    state = state,
                    onAddBook = { navController.navigate(Routes.editor()) },
                    onOpenBooks = { navigateMain(Routes.Books) },
                    onOpenShelf = { navigateMain(Routes.Shelf) },
                )
            }
            composable(Routes.Books) {
                BooksScreen(
                    state = state,
                    onQueryChange = viewModel::setQuery,
                    onAddBook = { navController.navigate(Routes.editor()) },
                    onEdit = { navController.navigate(Routes.editor(it.id)) },
                    onDelete = viewModel::deleteBook,
                    onCopy = { copy(it.title, "${it.title}\n${it.multilineCallNumber}") },
                    onRetry = viewModel::retry,
                )
            }
            composable(Routes.Shelf) {
                ShelfScreen(
                    state = state,
                    onAddBook = { navController.navigate(Routes.editor()) },
                    onCopyOrder = {
                        val text = state.shelfPositions.joinToString("\n\n") { position ->
                            "${(position.sortedIndex + 1).toPersianNumber()}. ${position.book.title}\n${position.book.multilineCallNumber}"
                        }
                        copy(context.getString(R.string.shelf_order), text)
                    },
                )
            }
            composable(Routes.Guide) { GuideScreen() }
            composable(Routes.Data) {
                DataScreen(
                    state = state,
                    exportJson = viewModel::exportJson,
                    decodeBackup = viewModel::decodeBackup,
                    onImport = viewModel::importBooks,
                    onDeleteAll = viewModel::deleteAll,
                    onMessage = ::notify,
                )
            }
            composable(
                route = Routes.Editor,
                arguments = listOf(navArgument("bookId") { type = NavType.LongType }),
            ) { entry ->
                val bookId = entry.arguments?.getLong("bookId") ?: 0L
                val existing = state.books.firstOrNull { it.id == bookId }
                when {
                    bookId != 0L && state.isLoading -> LoadingState()
                    bookId != 0L && existing == null -> EmptyState(
                        title = stringResource(R.string.book_not_found),
                        body = stringResource(R.string.book_not_found),
                        actionLabel = stringResource(R.string.back),
                        onAction = { navController.popBackStack() },
                    )
                    else -> BookEditorScreen(
                        existing = existing,
                        onSave = { draft, allowDuplicate, result ->
                            viewModel.saveBook(draft, existing, allowDuplicate, result)
                        },
                        onSaved = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
