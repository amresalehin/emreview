package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Fast scroller scroll bar component for the Gallery.
 * Provides intuitive visual scrub feedback with a floating date indicator.
 */
@Composable
fun GalleryFastScrollBar(
    totalItems: Int,
    firstVisibleIndex: Int,
    isScrolling: Boolean,
    currentDateLabel: String,
    modifier: Modifier = Modifier,
    onScrollTo: (Int) -> Unit
) {
    if (totalItems <= 6) return

    var isDragging by remember { mutableStateOf(false) }
    var trackHeightPx by remember { mutableFloatStateOf(1f) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    // Auto fade after inactivity
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isScrolling, isDragging) {
        if (isScrolling || isDragging) {
            isVisible = true
        } else {
            delay(1200)
            if (!isScrolling && !isDragging) {
                isVisible = false
            }
        }
    }

    val alphaAnim by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300),
        label = "scrollBarAlpha"
    )

    if (alphaAnim <= 0.01f) return

    val scrollFraction = if (isDragging) {
        dragFraction
    } else {
        (firstVisibleIndex.toFloat() / (totalItems - 1).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(52.dp)
            .alpha(alphaAnim)
            .onGloballyPositioned { coordinates ->
                trackHeightPx = coordinates.size.height.toFloat()
            }
            .pointerInput(totalItems) {
                detectTapGestures { tapOffset ->
                    if (trackHeightPx > 0f) {
                        val frac = (tapOffset.y / trackHeightPx).coerceIn(0f, 1f)
                        val target = (frac * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
                        onScrollTo(target)
                    }
                }
            }
            .pointerInput(totalItems) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        if (trackHeightPx > 0f) {
                            dragFraction = (offset.y / trackHeightPx).coerceIn(0f, 1f)
                            val target = (dragFraction * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
                            onScrollTo(target)
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (trackHeightPx > 0f) {
                            val newFraction = (dragFraction + (dragAmount.y / trackHeightPx)).coerceIn(0f, 1f)
                            dragFraction = newFraction
                            val target = (newFraction * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
                            onScrollTo(target)
                        }
                    }
                )
            }
            .testTag("gallery_fast_scrollbar")
    ) {
        // Track line
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .width(4.dp)
                .fillMaxHeight(0.9f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
        )

        // Thumb Handle container positioned along the track
        Box(
            modifier = Modifier
                .fillMaxHeight(0.85f)
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val availableHeight = maxHeight
                val thumbOffset = availableHeight * scrollFraction

                Row(
                    modifier = Modifier
                        .offset(y = thumbOffset)
                        .align(Alignment.TopEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Floating Date Bubble Indicator
                    if (currentDateLabel.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.94f),
                            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                            shadowElevation = 4.dp,
                            modifier = Modifier.testTag("scrollbar_date_bubble")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.inverseOnSurface
                                )
                                Text(
                                    text = currentDateLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    // Scrubber thumb pill
                    Surface(
                        shape = CircleShape,
                        color = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (isDragging) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(width = 16.dp, height = 36.dp)
                            .testTag("scrollbar_thumb")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.UnfoldMore,
                                contentDescription = "Scrollbar Handle",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
