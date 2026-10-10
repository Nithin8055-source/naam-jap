package com.naamjap.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.naamjap.app.feature.home.HomeScreen
import com.naamjap.app.feature.jap.JapScreen
import com.naamjap.app.feature.history.HistoryScreen
import com.naamjap.app.feature.insights.InsightsScreen
import com.naamjap.app.feature.settings.SettingsScreen
import com.naamjap.app.feature.jap.ManualRecordScreen
import com.naamjap.app.ui.components.GlassBottomBar
import com.naamjap.app.ui.components.PremiumNavigationOverlay
import com.naamjap.app.ui.theme.ThemeChoice
import com.naamjap.app.domain.model.AccountIdentity
import com.naamjap.app.domain.repository.PracticeRepository
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.ZoneId
import androidx.compose.ui.platform.LocalContext
import com.naamjap.app.widget.NaamJapWidgetRenderer
import com.naamjap.app.widget.NaamJapWidgetSnapshotStore

@HiltViewModel
class PracticeFeedbackViewModel @Inject constructor(
    private val repository: PracticeRepository
) : ViewModel() {
    val state = repository.state

    fun retry() {
        viewModelScope.launch {
            try {
                repository.refresh(ZoneId.systemDefault().id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The repository stores a safe, user-facing error in its state.
            }
        }
    }
}

@Composable
fun NaamJapApp(
    themeChoice: ThemeChoice,
    onThemeChoice: (ThemeChoice) -> Unit,
    account: AccountIdentity,
    onSignOut: () -> Unit,
    openJapRequest: Int = 0,
    openHomeRequest: Int = 0
) {
    val context = LocalContext.current
    val hazeState = rememberHazeState()
    val feedbackViewModel: PracticeFeedbackViewModel = hiltViewModel()
    val practiceState by feedbackViewModel.state.collectAsStateWithLifecycle()
    var savedPage by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = savedPage.coerceIn(0, Destination.primary.lastIndex),
        pageCount = { Destination.primary.size }
    )
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page -> savedPage = page }
    }
    LaunchedEffect(openJapRequest) {
        if (openJapRequest > 0) {
            val page = Destination.primary.indexOf(Destination.Jap)
            if (page >= 0) pagerState.animateScrollToPage(page)
        }
    }
    LaunchedEffect(openHomeRequest) {
        if (openHomeRequest > 0) pagerState.animateScrollToPage(Destination.primary.indexOf(Destination.Home))
    }
    LaunchedEffect(
        account.userId,
        practiceState.hasLoaded,
        practiceState.dashboard,
        practiceState.activeSession?.count,
        practiceState.error
    ) {
        if (!practiceState.hasLoaded) return@LaunchedEffect
        if (practiceState.error == null) {
            NaamJapWidgetSnapshotStore.write(
                context = context,
                userId = account.userId,
                count = practiceState.dashboard.todayCount,
                goal = practiceState.dashboard.dailyGoal,
                activeSessionId = practiceState.activeSession?.id,
                sessionPaused = practiceState.activeSession?.isPaused == true
            )
        } else {
            NaamJapWidgetSnapshotStore.setStatusForAccount(context, account.userId, "Needs sync - last confirmed")
        }
        NaamJapWidgetRenderer.updateAll(context)
    }
    val scope = rememberCoroutineScope()
    val manualRecordOpenState = remember { mutableStateOf(false) }
    val manualRecordOpen by manualRecordOpenState
    val currentRoute = Destination.primary.getOrNull(pagerState.currentPage)?.route
    BackHandler(enabled = manualRecordOpen) { manualRecordOpenState.value = false }

    PremiumNavigationOverlay(
        feedbackMessage = practiceState.error,
        onFeedbackRetry = if (practiceState.canRetry) feedbackViewModel::retry else null,
        floatingNavigation = {
            if (!manualRecordOpen) {
                GlassBottomBar(
                    selectedRoute = currentRoute,
                    onSelect = { destination ->
                        val page = Destination.primary.indexOf(destination)
                        if (page >= 0) scope.launch { pagerState.animateScrollToPage(page) }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp),
                    hazeState = hazeState
                )
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = manualRecordOpen,
            modifier = Modifier.fillMaxSize().padding(innerPadding).hazeSource(hazeState).imePadding(),
            transitionSpec = {
                if (targetState) {
                    (slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(animationSpec = tween(240)) { -it / 5 } + fadeOut(tween(180)))
                } else {
                    (slideInHorizontally(animationSpec = tween(300)) { -it / 5 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(animationSpec = tween(240)) { it } + fadeOut(tween(180)))
                }
            },
            label = "manual record navigation"
        ) { showingManualRecord ->
            if (showingManualRecord) {
                ManualRecordScreen(onBack = { manualRecordOpenState.value = false })
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    // Prepare only the adjacent screens so their initial composition
                    // does not land on the first frame of a user-driven swipe.
                    beyondViewportPageCount = 1,
                    key = { page -> Destination.primary[page].route }
                ) { page ->
                    when (Destination.primary[page]) {
                        Destination.Home -> HomeScreen(
                            displayName = account.displayName,
                            onNavigate = { route ->
                                if (route == Destination.ManualRecord.route) {
                                    manualRecordOpenState.value = true
                                } else {
                                    Destination.primary.indexOfFirst { it.route == route }
                                        .takeIf { it >= 0 }
                                        ?.let { target -> scope.launch { pagerState.animateScrollToPage(target) } }
                                }
                            }
                        )
                        Destination.Jap -> JapScreen()
                        Destination.History -> HistoryScreen()
                        Destination.Insights -> InsightsScreen()
                        Destination.Settings -> SettingsScreen(
                            themeChoice = themeChoice,
                            onThemeChoice = onThemeChoice,
                            account = account,
                            onSignOut = onSignOut
                        )
                        else -> Unit
                    }
                }
            }
        }
    }
}
