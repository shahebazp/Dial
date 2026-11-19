package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DotMatrixFontFamily
import com.example.ui.theme.NothingGreen
import com.example.ui.theme.NothingRed
import com.example.ui.theme.SpaceMonoFontFamily
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun NothingTopHeader(
    title: String,
    subtitle: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (trailingContent != null) {
                trailingContent()
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        NothingDottedDivider()
    }
}

@Composable
fun NothingGeometricOrb(
    size: Dp = 160.dp,
    isAnimated: Boolean = true,
    modifier: Modifier = Modifier
) {
    val outlineColor = MaterialTheme.colorScheme.outline
    val accentColor = NothingRed
    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(
            color = outlineColor,
            radius = radius * 0.92f,
            center = center,
            style = Stroke(
                width = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            )
        )
        drawCircle(
            color = outlineColor.copy(alpha = 0.6f),
            radius = radius * 0.62f,
            center = center,
            style = Stroke(width = 1.5f)
        )
        if (isAnimated) {
            drawCircle(
                color = accentColor,
                radius = 5.dp.toPx(),
                center = Offset(center.x + radius * 0.62f, center.y)
            )
        }
    }
}

@Composable
fun NothingDottedDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outline
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
    ) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f)
        )
    }
}

@Composable
fun CircularCallPulseAvatar(
    displayName: String,
    isActive: Boolean,
    isIncoming: Boolean,
    size: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "call_pulse")
    val pulseProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_ring"
    )

    val ringColor = when {
        isIncoming -> NothingRed
        isActive -> MaterialTheme.colorScheme.onBackground
        else -> MaterialTheme.colorScheme.outline
    }

    val initials = remember(displayName) {
        displayName.trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.take(1).uppercase() }
            .ifEmpty { "#" }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseRadius = this.size.minDimension / 2f
            if (isActive || isIncoming) {
                val outerRadius = baseRadius * (0.72f + 0.28f * pulseProgress)
                drawCircle(
                    color = ringColor.copy(alpha = (1f - pulseProgress) * 0.45f),
                    radius = outerRadius,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    )
                )
            }
            drawCircle(
                color = ringColor.copy(alpha = 0.65f),
                radius = baseRadius * 0.72f,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        Box(
            modifier = Modifier
                .size(size * 0.68f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                fontFamily = DotMatrixFontFamily,
                fontSize = (size.value * 0.24f).sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun VerticalIncomingCallSlider(
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val maxOffsetPx = with(density) { 88.dp.toPx() }
    val offsetY = remember { Animatable(0f) }
    var triggered by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(width = 84.dp, height = 248.dp)
            .clip(RoundedCornerShape(42.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(42.dp))
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ANSWER",
                fontFamily = SpaceMonoFontFamily,
                fontSize = 10.sp,
                color = NothingGreen
            )
            Text(
                text = "DECLINE",
                fontFamily = SpaceMonoFontFamily,
                fontSize = 10.sp,
                color = NothingRed
            )
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onBackground)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            val target = (offsetY.value + delta).coerceIn(-maxOffsetPx, maxOffsetPx)
                            offsetY.snapTo(target)
                        }
                    },
                    onDragStopped = {
                        val current = offsetY.value
                        when {
                            current <= -maxOffsetPx * 0.75f && !triggered -> {
                                triggered = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAnswer()
                            }
                            current >= maxOffsetPx * 0.75f && !triggered -> {
                                triggered = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onReject()
                            }
                            else -> {
                                offsetY.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
                            }
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (offsetY.value > 20f) Icons.Default.CallEnd else Icons.Default.Call,
                contentDescription = "Slide to answer or decline",
                tint = MaterialTheme.colorScheme.background,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun HorizontalSlideToAnswerBar(
    onAnswer: () -> Unit,
    onTouchStateChanged: (Boolean) -> Unit = {},
    isAnswered: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val trackHeight = 76.dp
    val cornerRadius = 38.dp
    val handleSize = 68.dp
    val internalPadding = 4.dp

    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var snapBackJob by remember { mutableStateOf<Job?>(null) }
    var thresholdCrossedHapticFired by remember { mutableStateOf(false) }
    var hasAnswered by remember(isAnswered) { mutableStateOf(isAnswered) }

    val infiniteTransition = rememberInfiniteTransition(label = "nothing_dot_wave")
    val dotWavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dot_wave_phase"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .height(trackHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0xCC242424))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.22f),
                shape = RoundedCornerShape(cornerRadius)
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val anyPressed = event.changes.any { it.pressed }
                        onTouchStateChanged(anyPressed)
                    }
                }
            }
            .padding(internalPadding)
            .testTag("incoming_slide_answer_bar"),
        contentAlignment = Alignment.CenterStart
    ) {
        val maxDragPx = with(density) {
            (maxWidth - handleSize).toPx().coerceAtLeast(1f)
        }
        val effectiveOffsetPx = if (hasAnswered || isAnswered) {
            maxDragPx
        } else {
            dragOffsetPx.coerceIn(0f, maxDragPx)
        }
        val dragFraction = (effectiveOffsetPx / maxDragPx).coerceIn(0f, 1f)
        val labelAlpha = (1f - dragFraction * 1.35f).coerceIn(0f, 1f)

        // Centered label + right-end Nothing dot-matrix detailing within the remaining track area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = handleSize, end = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Slide to answer",
                fontFamily = DotMatrixFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 17.sp,
                letterSpacing = 0.8.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .alpha(labelAlpha)
                    .padding(end = 24.dp)
            )

            // Subtle Nothing-style 3x3 dot-matrix sequential wave towards the right end
            Canvas(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp)
                    .size(width = 22.dp, height = 18.dp)
                    .alpha(labelAlpha)
            ) {
                val dotRadius = 1.25.dp.toPx() // 2.5dp diameter
                val colSpacing = 6.dp.toPx()
                val rowSpacing = 6.dp.toPx()
                val centerY = size.height / 2f

                for (col in 0..2) {
                    val distance = abs(dotWavePhase - col)
                    val colAlpha = (0.20f + (1f - distance.coerceIn(0f, 1f)) * 0.65f)
                        .coerceIn(0.20f, 0.85f)
                    val cx = dotRadius + col * colSpacing
                    for (row in -1..1) {
                        val cy = centerY + row * rowSpacing
                        drawCircle(
                            color = Color.White.copy(alpha = colAlpha),
                            radius = dotRadius,
                            center = Offset(cx, cy)
                        )
                    }
                }
            }
        }

        // Draggable 68dp White Circular Handle with 28dp Black Phone Icon
        Box(
            modifier = Modifier
                .offset { IntOffset(effectiveOffsetPx.roundToInt(), 0) }
                .size(handleSize)
                .clip(CircleShape)
                .background(Color.White)
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = !hasAnswered && !isAnswered,
                    state = rememberDraggableState { delta ->
                        if (hasAnswered || isAnswered) return@rememberDraggableState
                        snapBackJob?.cancel()
                        val updated = (dragOffsetPx + delta).coerceIn(0f, maxDragPx)
                        dragOffsetPx = updated
                        val fraction = updated / maxDragPx
                        if (fraction >= 0.85f && !thresholdCrossedHapticFired) {
                            thresholdCrossedHapticFired = true
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } else if (fraction < 0.80f) {
                            thresholdCrossedHapticFired = false
                        }
                    },
                    onDragStarted = {
                        snapBackJob?.cancel()
                        onTouchStateChanged(true)
                    },
                    onDragStopped = {
                        onTouchStateChanged(false)
                        if (hasAnswered || isAnswered) return@draggable
                        val fraction = (dragOffsetPx / maxDragPx).coerceIn(0f, 1f)
                        if (fraction >= 0.85f) {
                            hasAnswered = true
                            dragOffsetPx = maxDragPx
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAnswer()
                        } else {
                            thresholdCrossedHapticFired = false
                            val startValue = dragOffsetPx
                            snapBackJob = scope.launch {
                                val anim = Animatable(startValue)
                                anim.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        durationMillis = 200,
                                        easing = FastOutSlowInEasing
                                    )
                                ) {
                                    dragOffsetPx = value
                                }
                            }
                        }
                    }
                )
                .testTag("slide_answer_thumb"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Slide right to answer call",
                tint = Color.Black,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
