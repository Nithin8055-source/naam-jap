package com.naamjap.app.feature.jap

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naamjap.app.ui.components.navigationContentBottomInset
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.asStateFlow
import android.os.SystemClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import com.naamjap.app.ui.components.*
import com.naamjap.app.ui.theme.JapSpacing
import com.naamjap.app.ui.theme.NaamJapTheme
import com.naamjap.app.R
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.cos
import kotlin.math.sin

data class JapUiState(val title: String = "Naam Jap", val count: Int = 0)

@HiltViewModel
class JapViewModel @Inject constructor(private val repository: com.naamjap.app.domain.repository.PracticeRepository) : ViewModel() {
    val data = repository.state
    data class OptimisticCount(val sessionId: String, val target: Long)
    private data class QueuedIncrement(val sessionId: String, val operationId: String, val target: Long)
    private val _optimisticCount = MutableStateFlow<OptimisticCount?>(null)
    val optimisticCount = _optimisticCount.asStateFlow()
    private val increments = Channel<QueuedIncrement>(Channel.UNLIMITED)
    private val pendingActions = mutableMapOf<Pair<String, com.naamjap.app.domain.repository.SessionAction>, String>()
    private val actionsInFlight = mutableSetOf<Pair<String, com.naamjap.app.domain.repository.SessionAction>>()
    private val _isUserRefreshing = MutableStateFlow(false)
    val isUserRefreshing = _isUserRefreshing.asStateFlow()
    private val _isSessionActionPending = MutableStateFlow(false)
    val isSessionActionPending = _isSessionActionPending.asStateFlow()
    private var refreshJob: kotlinx.coroutines.Job? = null

    init {
        refresh()
        viewModelScope.launch {
            for (increment in increments) {
                var succeeded = false
                for (attempt in 0 until 3) {
                    if (attempt > 0) delay(250L shl (attempt - 1))
                    try {
                        repository.applySessionAction(increment.sessionId, com.naamjap.app.domain.repository.SessionAction.INCREMENT, increment.operationId)
                        succeeded = true
                        break
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Retry the same operation ID to avoid double counting an uncertain response.
                    }
                }
                if (!succeeded) {
                    _optimisticCount.value = null
                    while (increments.tryReceive().isSuccess) Unit
                    continue
                }
                val serverCount = repository.state.value.activeSession?.takeIf { it.id == increment.sessionId }?.count ?: 0L
                _optimisticCount.value = _optimisticCount.value?.let { current ->
                    if (current.sessionId == increment.sessionId && current.target <= serverCount) null else current
                }
            }
        }
    }

    fun refresh(userInitiated: Boolean = false) {
        if (refreshJob?.isActive == true) {
            if (userInitiated) _isUserRefreshing.value = true
            return
        }
        refreshJob = viewModelScope.launch {
            _isUserRefreshing.value = userInitiated
            try {
                repository.refresh(java.time.ZoneId.systemDefault().id)
                if (repository.state.value.naamTypes.isEmpty()) repository.ensureDefaultNaamType()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Repository state retains its last successful session and publishes the load error.
            } finally {
                _isUserRefreshing.value = false
            }
        }
    }

    fun start(id: String) = viewModelScope.launch {
        try {
            repository.startSession(id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The repository exposes a safe error in its state.
        }
    }

    fun action(id: String, action: com.naamjap.app.domain.repository.SessionAction) {
        if (_isSessionActionPending.value) return
        val key = id to action
        if (!actionsInFlight.add(key)) return
        _isSessionActionPending.value = true
        val operationId = pendingActions.getOrPut(key) { java.util.UUID.randomUUID().toString() }
        viewModelScope.launch {
            try {
                repository.applySessionAction(id, action, operationId)
                pendingActions.remove(key)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Retain the ID so the next user retry remains idempotent.
            } finally {
                actionsInFlight.remove(key)
                _isSessionActionPending.value = actionsInFlight.isNotEmpty()
            }
        }
    }
    fun increment(sessionId: String) {
        val serverCount = repository.state.value.activeSession?.takeIf { it.id == sessionId }?.count ?: 0L
        val previousTarget = _optimisticCount.value?.takeIf { it.sessionId == sessionId }?.target ?: serverCount
        val currentCount = maxOf(previousTarget, serverCount)
        if (currentCount == Long.MAX_VALUE) return
        val target = currentCount + 1
        _optimisticCount.value = OptimisticCount(sessionId, target)
        increments.trySend(QueuedIncrement(sessionId, java.util.UUID.randomUUID().toString(), target))
    }
    suspend fun save(id: String, count: Long, note: String?, naamId: String, date: LocalDate) {
        repository.saveManualRecord(
            com.naamjap.app.domain.model.PracticeRecord(id, count, System.currentTimeMillis(), note),
            naamId,
            date
        )
    }
}

@Composable
fun JapScreen(viewModel: JapViewModel = hiltViewModel()) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val defaultNaam = data.naamTypes.firstOrNull { it.isDefault } ?: data.naamTypes.firstOrNull()
    val isUserRefreshing by viewModel.isUserRefreshing.collectAsStateWithLifecycle()
    val active = data.activeSession
    val optimisticCount by viewModel.optimisticCount.collectAsStateWithLifecycle()
    val isSessionActionPending by viewModel.isSessionActionPending.collectAsStateWithLifecycle()
    val displayedCount = maxOf(active?.count ?: 0L, optimisticCount?.takeIf { it.sessionId == active?.id }?.target ?: 0L)
    val formattedCount = com.naamjap.app.ui.components.formatCount(displayedCount)
    var liveDurationSeconds by remember(active?.id) { mutableLongStateOf(active?.durationSeconds ?: 0L) }
    LaunchedEffect(active?.id, active?.durationSeconds, active?.isPaused) {
        val session = active ?: return@LaunchedEffect
        val baseDuration = session.durationSeconds
        val startRealtime = SystemClock.elapsedRealtime()
        liveDurationSeconds = baseDuration
        if (!session.isPaused && session.endedAt == null) {
            while (true) {
                delay(1_000)
                liveDurationSeconds = baseDuration + (SystemClock.elapsedRealtime() - startRealtime) / 1_000
            }
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val countInteraction = remember { MutableInteractionSource() }
    val countPressed by countInteraction.collectIsPressedAsState()
    val countScale by androidx.compose.animation.core.animateFloatAsState(if (countPressed) .96f else 1f, label = "count button press")
    val haptics = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isAppResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { isAppResumed = true }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { isAppResumed = false }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    PremiumPullToRefreshBox(
        isRefreshing = isUserRefreshing,
        onRefresh = { viewModel.refresh(userInitiated = true) },
        modifier = Modifier.fillMaxSize()
    ) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = JapSpacing.xl, top = JapSpacing.lg, end = JapSpacing.xl)
            .padding(bottom = navigationContentBottomInset()), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Naam Jap", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(JapSpacing.xs))
            Text(if (active == null) "Your default naam is ready" else if (active.isPaused) "Paused" else "Session in progress", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(JapSpacing.xl))
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val ringSize = maxWidth.coerceAtMost(272.dp)
                // Reclaim the column's horizontal padding for decoration so a narrow
                // screen still has room for petals outside the unchanged counter ring.
                val flowerSize = (ringSize * 1.35f).coerceAtMost(maxWidth + JapSpacing.xl * 2f)
                Box(Modifier.requiredSize(flowerSize), contentAlignment = Alignment.Center) {
                    LotusCounterFrame(
                        ringDiameter = ringSize,
                        modifier = Modifier.fillMaxSize()
                    )
                    Image(
                        painter = painterResource(R.drawable.ic_sacred_halo),
                        contentDescription = null,
                        modifier = Modifier.size(ringSize * 0.9f).alpha(0.2f),
                        contentScale = ContentScale.Fit
                    )
                    AnimatedNaamRing(
                        Modifier.size(ringSize),
                        strokeWidth = 5.dp,
                        isActive = isAppResumed && active != null && !active.isPaused && active.endedAt == null
                    ) {
                        val countFontSize = when {
                            formattedCount.length > 16 -> 15.sp
                            formattedCount.length > 12 -> 20.sp
                            formattedCount.length > 9 -> 26.sp
                            formattedCount.length > 7 -> 32.sp
                            formattedCount.length > 5 -> 42.sp
                            else -> 52.sp
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LotusMark(Modifier.size(44.dp).offset(y = (-5).dp), null)
                            AnimatedContent(
                                targetState = formattedCount,
                                modifier = Modifier.fillMaxWidth(.9f).height(62.dp),
                                contentAlignment = Alignment.Center,
                                label = "session count"
                            ) { count ->
                                Text(
                                    count,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        fontSize = countFontSize,
                                        lineHeight = (countFontSize.value * 1.1f).sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        fontFeatureSettings = "tnum"
                                    ),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Text("Naam Jap", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                active?.naamName ?: defaultNaam?.name ?: "Add a naam in Settings",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.offset(y = 6.dp),
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(JapSpacing.sm))
            Text("A calm space for your practice", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(JapSpacing.xl))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                SessionActionControl(
                    icon = Icons.Default.Replay,
                    label = "Undo",
                    enabled = active != null && displayedCount > 0 && !isSessionActionPending,
                    onClick = { active?.let { viewModel.action(it.id, com.naamjap.app.domain.repository.SessionAction.UNDO) } }
                )
                Button(
                    onClick = { active?.let { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.increment(it.id) } },
                    enabled = active != null && !active.isPaused && !isSessionActionPending,
                    interactionSource = countInteraction,
                    modifier = Modifier.size(76.dp).scale(countScale).shadow(5.dp, CircleShape, clip = false),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = .22f)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 2.dp, focusedElevation = 2.dp, hoveredElevation = 2.dp)
                ) {
                    LotusMark(Modifier.size(34.dp), "Add one Naam Jap")
                }
                SessionActionControl(
                    icon = if (active?.isPaused == true) Icons.Default.PlayArrow else Icons.Default.Pause,
                    label = if (active?.isPaused == true) "Resume" else "Pause",
                    enabled = active != null && !isSessionActionPending,
                    onClick = {
                        active?.let {
                            viewModel.action(it.id, if (it.isPaused) com.naamjap.app.domain.repository.SessionAction.RESUME else com.naamjap.app.domain.repository.SessionAction.PAUSE)
                        }
                    }
                )
            }
            Spacer(Modifier.height(JapSpacing.xs))
            Text(if (active == null) "Start a session to count" else "Tap to add one repetition", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(JapSpacing.xl))
            GlassSurface(Modifier.fillMaxWidth()) {
                val shownCount = maxOf(active?.count ?: 0L, optimisticCount?.takeIf { it.sessionId == active?.id }?.target ?: 0L)
                Column(Modifier.padding(horizontal = JapSpacing.md, vertical = JapSpacing.sm), verticalArrangement = Arrangement.spacedBy(JapSpacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JapSpacing.sm)) {
                        Image(
                            painter = painterResource(R.drawable.mala_beads),
                            contentDescription = "Mala beads",
                            modifier = Modifier.size(42.dp).clip(CircleShape).border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = .38f),
                                CircleShape
                            ),
                            contentScale = ContentScale.Crop
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "CURRENT SESSION",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = .8.sp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                active?.naamName ?: "Ready to begin",
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                if (active == null) "No active session" else formatElapsed(liveDurationSeconds),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .8f)))
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            val countLabel = com.naamjap.app.ui.components.formatCount(shownCount)
                            Text(
                                countLabel,
                                style = when {
                                    countLabel.length > 13 -> MaterialTheme.typography.labelSmall
                                    countLabel.length > 9 -> MaterialTheme.typography.labelLarge
                                    else -> MaterialTheme.typography.titleMedium
                                }.copy(fontFeatureSettings = "tnum"),
                                maxLines = 1,
                                softWrap = false
                            )
                            Text("counts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (active == null && defaultNaam != null) {
                        TextButton(onClick = { viewModel.start(defaultNaam.id) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                            Text("Start ${defaultNaam.name}", maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    } else if (active != null) {
                        TextButton(
                            onClick = { viewModel.action(active.id, com.naamjap.app.domain.repository.SessionAction.FINISH) },
                            enabled = !data.isSaving && !isSessionActionPending && (optimisticCount?.takeIf { it.sessionId == active.id }?.target ?: active.count) <= active.count,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) { Text("Finish session") }
                    }
                }
            }
        }
        data.error?.let { Text(it, Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp, start = 20.dp, end = 20.dp), color = MaterialTheme.colorScheme.error) }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
    }
}

@Composable
private fun PreviewControl(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = {}, enabled = false, modifier = Modifier.size(48.dp)) { Icon(icon, contentDescription = null) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AnimatedNaamRing(
    modifier: Modifier = Modifier,
    strokeWidth: androidx.compose.ui.unit.Dp,
    isActive: Boolean,
    content: @Composable BoxScope.() -> Unit
) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(isActive) {
        if (isActive) {
            while (true) {
                val remainingDegrees = (360f - rotation.value).coerceAtLeast(1f)
                val remainingDuration = (10_000 * remainingDegrees / 360f).toInt().coerceAtLeast(1)
                rotation.animateTo(360f, tween(remainingDuration, easing = LinearEasing))
                rotation.snapTo(0f)
            }
        }
    }
    val trackColor = MaterialTheme.colorScheme.secondary
    val lightColor = MaterialTheme.colorScheme.primary
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val radius = size.minDimension / 2f - stroke / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(trackColor.copy(alpha = .48f), radius, center, style = Stroke(stroke))
            if (isActive) {
                val angle = Math.toRadians((rotation.value - 90f).toDouble()).toFloat()
                val lightCenter = Offset(center.x + radius * cos(angle), center.y + radius * sin(angle))
                val haloRadius = 28.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = .12f),
                            lightColor.copy(alpha = .11f),
                            Color.Transparent
                        ),
                        center = lightCenter,
                        radius = haloRadius
                    ),
                    radius = haloRadius,
                    center = lightCenter
                )
                val glowRadius = 12.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = .62f),
                            lightColor.copy(alpha = .28f),
                            Color.Transparent
                        ),
                        center = lightCenter,
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = lightCenter
                )
                drawCircle(Color.White.copy(alpha = .96f), radius = 2.2.dp.toPx(), center = lightCenter)
            }
        }
        content()
    }
}

@Composable
private fun SessionActionControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(52.dp).shadow(2.dp, CircleShape).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatElapsed(seconds: Long): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    val remainder = safeSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder)
    else "%02d:%02d".format(minutes, remainder)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ManualRecordScreen(onBack: () -> Unit) {
    val viewModel: JapViewModel = hiltViewModel()
    val data by viewModel.data.collectAsStateWithLifecycle()
    var count by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var mantraId by rememberSaveable { mutableStateOf("") }
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showMantraMenu by remember { mutableStateOf(false) }
    var saveAttempted by remember { mutableStateOf(false) }
    var pendingRecordId by rememberSaveable { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(selectedDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = JapSpacing.lg, vertical = JapSpacing.md), verticalArrangement = Arrangement.spacedBy(JapSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PremiumIconButton("Go back", onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                Text("Add manual record", Modifier.padding(start = JapSpacing.sm), style = MaterialTheme.typography.headlineMedium)
            }
            Text("Add a practice record", style = MaterialTheme.typography.titleMedium)
            Box {
                OutlinedTextField(value = data.naamTypes.firstOrNull { it.id == mantraId }?.name.orEmpty(), onValueChange = {}, readOnly = true, label = { Text("Naam / Mantra") }, trailingIcon = { TextButton(onClick = { showMantraMenu = true }) { Text("Choose") } }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                DropdownMenu(expanded = showMantraMenu, onDismissRequest = { showMantraMenu = false }) {
                    data.naamTypes.forEach { naam -> DropdownMenuItem(text = { Text(naam.name) }, onClick = { mantraId = naam.id; showMantraMenu = false }) }
                }
            }
            val parsedCount = count.toLongOrNull()
            PremiumTextField(
                value = count,
                onValueChange = { count = it.filter { digit -> digit in '0'..'9' }.take(19) },
                label = "Naam Jap count",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = saveAttempted && (parsedCount == null || parsedCount < 1),
                supportingText = when {
                    !saveAttempted -> null
                    count.isBlank() -> "Enter a count to continue"
                    parsedCount == null || parsedCount < 1 -> "Enter a positive count within the supported numeric range"
                    else -> null
                }
            )
            OutlinedTextField(value = LocalDate.parse(selectedDate).format(dateFormatter), onValueChange = {}, readOnly = true, label = { Text("Date") }, trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.CalendarMonth, contentDescription = "Choose date") } }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
            PremiumTextField(value = note, onValueChange = { note = it.take(400) }, label = "Notes (optional)", singleLine = false, supportingText = "${note.length}/400")
            data.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PrimaryActionButton("Save record", enabled = !data.isSaving, onClick = {
                saveAttempted = true
                scope.launch {
                    val amount = count.toLongOrNull()
                    val naamId = mantraId.ifBlank { data.naamTypes.firstOrNull { it.isDefault }?.id.orEmpty() }
                    if (amount == null || amount < 1) snackbarHostState.showSnackbar("Enter a count greater than zero.")
                    else if (naamId.isBlank()) snackbarHostState.showSnackbar("Choose a naam first.")
                    else try {
                        viewModel.save(pendingRecordId, amount, note, naamId, LocalDate.parse(selectedDate))
                        onBack()
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        snackbarHostState.showSnackbar(data.error ?: "Record could not be confirmed. Retry the save safely.")
                    }
                }
            })
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
    if (showDatePicker) {
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis -> selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                showDatePicker = false
            }) { Text("Apply") }
        }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }) { DatePicker(state = datePickerState) }
    }
}

@Preview(showBackground = true)
@Composable
private fun JapScreenPreview() {
    NaamJapTheme { Text("Naam Jap") }
}

@Preview(showBackground = true)
@Composable
private fun ManualRecordScreenPreview() {
    NaamJapTheme { ManualRecordScreen(onBack = {}) }
}
