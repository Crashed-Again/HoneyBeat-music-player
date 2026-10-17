package com.neonbear.honeybeat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.neonbear.honeybeat.Playlist
import com.neonbear.honeybeat.Track
import java.io.File

@Composable
fun Cover(model: String?, modifier: Modifier = Modifier, radius: Dp = 16.dp) {
    Box(
        modifier.clip(RoundedCornerShape(radius)).background(HB.Chip),
        contentAlignment = Alignment.Center
    ) {
        if (model == null) {
            Text("\u266A", style = tBody(22, HB.Muted))
        } else {
            AsyncImage(
                model = if (model.startsWith("/")) File(model) else model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String) {
    Column(Modifier.statusBarsPadding().padding(horizontal = 22.dp).padding(top = 24.dp)) {
        Text(title, style = tDisplay(50))
        Spacer(Modifier.height(10.dp))
        Text(subtitle, style = tBody(17, HB.Muted))
    }
}

@Composable
fun SearchField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(31.dp))
            .background(Color.White.copy(alpha = 0.11f))
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) Text(placeholder, style = tBody(19, HB.Muted))
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = tBody(19),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = if (onSearch != null) ImeAction.Search else ImeAction.Done),
            keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }, onDone = { }),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun PlaylistCard(p: Playlist, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(HB.Card)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(p.cover, Modifier.size(78.dp), 18.dp)
        Column(Modifier.weight(1f).padding(start = 18.dp)) {
            Text(p.name, style = tBold(21), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text("${p.tracks.size} songs", style = tBody(17, HB.Muted))
        }
        Text(">", style = tBody(24, HB.Muted), modifier = Modifier.padding(end = 10.dp))
    }
}

@Composable
fun TrackRow(t: Track, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(t.cover, Modifier.size(54.dp), 12.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(t.title, style = tBold(17), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (t.artist.isNotBlank()) {
                Text(t.artist, style = tBody(14, HB.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun PillButton(label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(if (active) accent.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.5.dp, if (active) accent else HB.Outline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = tBold(15, if (active) accent else Color(0xFFCFCFD4)))
    }
}

@Composable
fun NavPill(selected: Int, onTab: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(40.dp))
            .background(HB.NavBar)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf("Library", "Search", "Options").forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(if (sel) HB.NavActive else Color.Transparent)
                    .clickable { onTab(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = tDisplay(17, if (sel) Color.White else HB.Muted))
            }
        }
    }
}
