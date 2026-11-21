package com.d4nzxml.kytheraai

import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

val Bg = Color(0xFF0F0F12)
val Surf = Color(0xFF1A1A20)
val Line = Color(0xFF2A2A33)
val Txt = Color(0xFFECECF1)
val Sub = Color(0xFF9A9AA8)
val Purple = Color(0xFF7C5CFF)
val Brand = Brush.linearGradient(listOf(Color(0xFF7C5CFF), Color(0xFF3FA9FF)))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Purple, background = Bg, surface = Surf, onSurface = Txt)) {
                App()
            }
        }
    }
}

@Composable
fun App(vm: ChatViewModel = viewModel()) {
    var settings by remember { mutableStateOf(vm.active.key.isBlank()) }
    Box(Modifier.fillMaxSize().background(Bg)) {
        if (settings) SettingsScreen(vm) { settings = false } else ChatScreen(vm) { settings = true }
    }
}

@Composable
fun ChatScreen(vm: ChatViewModel, openSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.kythera_logo), null, Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Text("Kythera AI", color = Txt, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            ModelChip(vm)
            IconButton({ vm.newChat() }) { Icon(Icons.Default.Add, "Chat baru", tint = Txt) }
            IconButton(openSettings) { Icon(Icons.Default.Settings, "Pengaturan", tint = Txt) }
        }
        HorizontalDivider(color = Line)
        Box(Modifier.weight(1f)) { if (vm.messages.isEmpty()) EmptyState(vm) else MessageList(vm) }
        vm.error?.let { e ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF3A1A22)).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(e, color = Color(0xFFFFB4C0), fontSize = 13.sp, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                TextButton({ vm.retry() }) { Text("Coba lagi", color = Color.White) }
            }
        }
        InputBar(vm)
    }
}

@Composable
fun EmptyState(vm: ChatViewModel) {
    val tips = listOf("Jelaskan cara kerja ffmpeg dengan sederhana", "Buatkan script untuk kompres video", "Ringkas artikel yang saya tempel", "Bantu saya menulis caption TikTok")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.kythera_logo), null, Modifier.size(96.dp))
        Spacer(Modifier.height(16.dp))
        Text("Halo, ada yang bisa Kythera bantu?", color = Txt, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        tips.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Surf).border(1.dp, Line, RoundedCornerShape(16.dp))
                        .clickable { vm.send(t) }.padding(14.dp)) { Text(t, color = Txt, fontSize = 14.sp, lineHeight = 19.sp) }
                }
            }
        }
    }
}

@Composable
fun MessageList(vm: ChatViewModel) {
    val st = rememberLazyListState()
    LaunchedEffect(vm.messages.size) { st.animateScrollToItem(0) }
    LazyColumn(state = st, reverseLayout = true, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        items(vm.messages.size, key = { vm.messages.size - 1 - it }) { i ->
            val m = vm.messages[vm.messages.size - 1 - i]
            if (m.user) UserBubble(m.text) else AiMessage(m.text, vm.busy && i == 0)
        }
    }
}

@Composable
fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)).background(Brand).padding(14.dp, 10.dp)) {
            Text(text, color = Color.White, fontSize = 15.sp, lineHeight = 22.sp)
        }
    }
}

@Composable
fun AiMessage(text: String, loading: Boolean) {
    val clip = LocalClipboardManager.current
    Row(Modifier.fillMaxWidth()) {
        Image(painterResource(R.drawable.kythera_logo), null, Modifier.padding(top = 2.dp).size(26.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (text.isEmpty() && loading) Row(verticalAlignment = Alignment.CenterVertically) { Dots(); Spacer(Modifier.width(10.dp)); Text("Berpikir…", color = Sub, fontSize = 13.sp) } else {
                text.split("```").forEachIndexed { i, part ->
                    if (i % 2 == 1) CodeBlock(part)
                    else if (part.isNotBlank()) Text(md(part.trim('\n')), color = Txt, fontSize = 15.5.sp, lineHeight = 24.sp)
                }
                if (!loading) TextButton({ clip.setText(AnnotatedString(text)) }, contentPadding = PaddingValues(0.dp)) {
                    Text("Salin", color = Sub, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun CodeBlock(part: String) {
    val clip = LocalClipboardManager.current
    val lang = part.substringBefore('\n').trim()
    val code = part.substringAfter('\n', part).trimEnd()
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.padding(vertical = 6.dp).fillMaxWidth().clip(shape).background(Color(0xFF16161B)).border(1.dp, Line, shape)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(lang.ifBlank { "kode" }, color = Sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton({ clip.setText(AnnotatedString(code)) }) { Text("Salin", color = Color(0xFF9D8CFF), fontSize = 12.sp) }
        }
        Text(code, color = Txt, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
    }
}

@Composable
fun Dots() {
    val t = rememberInfiniteTransition(label = "dots")
    Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 150), RepeatMode.Reverse), label = "a$i")
            Box(Modifier.size(8.dp).alpha(a).background(Brand, CircleShape))
        }
    }
}

fun md(s: String): AnnotatedString = buildAnnotatedString {
    s.lines().forEachIndexed { li, raw ->
        if (li > 0) append("\n")
        var line = raw
        val head = line.startsWith("#")
        if (head) line = line.trimStart('#').trim()
        if (line.startsWith("- ") || line.startsWith("* ")) line = "•  " + line.drop(2)
        if (head) pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp))
        var i = 0
        while (i < line.length) {
            when {
                line.startsWith("**", i) && line.indexOf("**", i + 2) > 0 -> {
                    val e = line.indexOf("**", i + 2)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line.substring(i + 2, e)) }
                    i = e + 2
                }
                line[i] == '`' && line.indexOf('`', i + 1) > 0 -> {
                    val e = line.indexOf('`', i + 1)
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Line)) { append(line.substring(i + 1, e)) }
                    i = e + 1
                }
                else -> { append(line[i]); i++ }
            }
        }
        if (head) pop()
    }
}

@Composable
fun InputBar(vm: ChatViewModel) {
    var text by remember { mutableStateOf("") }
    val shape = RoundedCornerShape(26.dp)
    val canSend = text.isNotBlank()
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
        Row(Modifier.weight(1f).clip(shape).background(Surf).border(1.dp, Line, shape)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
            BasicTextField(text, { text = it }, Modifier.weight(1f).padding(vertical = 10.dp),
                textStyle = TextStyle(color = Txt, fontSize = 16.sp), cursorBrush = SolidColor(Purple), maxLines = 6,
                decorationBox = { inner -> Box { if (text.isEmpty()) Text("Tulis pesan untuk Kythera…", color = Sub, fontSize = 16.sp); inner() } })
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(44.dp).clip(CircleShape).background(if (vm.busy || canSend) Brand else SolidColor(Line))
                .clickable(enabled = vm.busy || canSend) { if (vm.busy) vm.stop() else { vm.send(text.trim()); text = "" } },
                contentAlignment = Alignment.Center) {
                if (vm.busy) Box(Modifier.size(14.dp).background(Color.White, RoundedCornerShape(3.dp)))
                else Icon(Icons.AutoMirrored.Filled.Send, "Kirim", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun ModelChip(vm: ChatViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.clip(RoundedCornerShape(16.dp)).background(Surf).border(1.dp, Line, RoundedCornerShape(16.dp))
            .clickable { open = true }.padding(12.dp, 6.dp)) {
            Text(vm.active.name + " ▾", color = Txt, fontSize = 13.sp)
        }
        DropdownMenu(open, { open = false }) {
            vm.profiles.forEach { q ->
                DropdownMenuItem(text = { Text(q.name + if (q.model.isNotBlank()) "  ·  " + q.model else "") },
                    onClick = { vm.select(q.id); open = false })
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: ChatViewModel, close: () -> Unit) {
    BackHandler(onBack = close)
    val list = remember { mutableStateListOf<Profile>().apply { addAll(vm.profiles) } }
    var sel by remember { mutableStateOf(vm.activeId) }
    var sys by remember { mutableStateOf(vm.system) }
    val i = list.indexOfFirst { it.id == sel }.coerceAtLeast(0)
    val p = list[i]
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(close) { Icon(Icons.Default.Close, "Tutup", tint = Txt) }
            Text("Pengaturan", color = Txt, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Text("Penyedia model", color = Sub, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { q ->
                val on = q.id == sel
                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) Purple else Surf)
                    .border(1.dp, if (on) Purple else Line, RoundedCornerShape(20.dp))
                    .clickable { sel = q.id }.padding(14.dp, 8.dp)) {
                    Text(q.name + if (q.key.isNotBlank()) " ✓" else "", color = Color.White, fontSize = 14.sp)
                }
            }
        }
        val hint = when (p.type) {
            "gemini" -> "Ambil gratis di aistudio.google.com/apikey. Disimpan hanya di HP kamu."
            "anthropic" -> "Buat di console.anthropic.com (berbayar). Disimpan hanya di HP kamu."
            else -> "Key dari penyedia (DashScope, OpenRouter, dll). Disimpan hanya di HP kamu."
        }
        Field("API key ${p.name}", p.key, { list[i] = p.copy(key = it.trim()) }, hint, true)
        Field("Model", p.model, { list[i] = p.copy(model = it.trim()) }, "Tulis nama model persis seperti di dokumentasi penyedia.")
        if (p.type == "openai") Field("Base URL", p.baseUrl, { list[i] = p.copy(baseUrl = it.trim()) }, "Alamat API kompatibel OpenAI, tanpa /chat/completions.")
        Field("Instruksi kustom (opsional)", sys, { sys = it }, "Gaya atau aturan jawaban Kythera, berlaku untuk semua model.", false, false)
        Spacer(Modifier.height(8.dp))
        Button({ vm.saveProfiles(list.toList(), sel, sys); close() }, Modifier.fillMaxWidth().height(50.dp),
            enabled = p.key.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("Simpan & pakai ${p.name}", color = Color.White) }
    }
}

@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, hint: String, secret: Boolean = false, single: Boolean = true) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = single, minLines = if (single) 1 else 3,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Txt, unfocusedTextColor = Txt, focusedBorderColor = Purple,
            unfocusedBorderColor = Line, cursorColor = Purple, focusedLabelColor = Purple, unfocusedLabelColor = Sub))
    Text(hint, color = Sub, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 16.dp))
}
