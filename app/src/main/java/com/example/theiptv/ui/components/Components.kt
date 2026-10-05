package com.example.theiptv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.theiptv.ui.theme.AccentBlue
import com.example.theiptv.ui.theme.AppButtonHeight
import com.example.theiptv.ui.theme.AppButtonShape
import com.example.theiptv.ui.theme.AppButtonWithIconContentPadding
import com.example.theiptv.ui.theme.NightSurfaceAlt

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CredentialField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    compact: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (compact) 10.dp else 12.dp)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .background(NightSurfaceAlt, shape)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) AccentBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    shape = shape
                )
                .padding(
                    horizontal = if (compact) 10.dp else 14.dp,
                    vertical = if (compact) 8.dp else 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(if (compact) 16.dp else 20.dp)
                )
                Spacer(Modifier.width(if (compact) 8.dp else 10.dp))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                visualTransformation = if (isPassword) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                cursorBrush = SolidColor(AccentBlue),
                textStyle = (if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge).copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoadingBox(message: String, modifier: Modifier = Modifier) {
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "angle"
    )
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier
                    .size(42.dp)
                    .rotate(angle)
            )
            Spacer(Modifier.height(16.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    scrolling: Boolean = false,
) {
    Text(
        text = text,
        color = color,
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = if (scrolling) TextOverflow.Clip else TextOverflow.Ellipsis,
        modifier = if (scrolling) {
            modifier.basicMarquee(
                iterations = Int.MAX_VALUE,
                initialDelayMillis = 400,
                repeatDelayMillis = 1000,
                velocity = 36.dp,
            )
        } else {
            modifier
        },
    )
}

@Composable
fun RemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    requestWidth: Dp = 160.dp,
    requestHeight: Dp = 240.dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val request = remember(url, requestWidth, requestHeight, density) {
        val widthPx = with(density) { requestWidth.roundToPx() }.coerceAtLeast(1)
        val heightPx = with(density) { requestHeight.roundToPx() }.coerceAtLeast(1)
        ImageRequest.Builder(context)
            .data(url)
            .size(widthPx, heightPx)
            .precision(Precision.INEXACT)
            .memoryCacheKey(url)
            .diskCacheKey(url)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FavoriteToggleButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = ButtonDefaults.shape(AppButtonShape),
        contentPadding = AppButtonWithIconContentPadding,
        modifier = modifier.height(AppButtonHeight),
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(if (isFavorite) "Favourited" else "Add to favourites")
    }
}
