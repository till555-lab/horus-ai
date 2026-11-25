package com.horusai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.launch

private val Gold = Color(0xFFF5B942)
private val Navy = Color(0xFF101426)
private val Panel = Color(0xFF171C33)
private val Panel2 = Color(0xFF222844)
private val Ink = Color(0xFFEDEFF7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val st = AppState(applicationContext)
        setContent { HorusTheme { App(st) } }
    }
}

@Composable
fun HorusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold, onPrimary = Navy,
            background = Navy, onBackground = Ink,
            surface = Panel, onSurface = Ink,
            surfaceVariant = Panel2, onSurfaceVariant = Ink,
            secondaryContainer = Panel2, onSecondaryContainer = Ink,
            errorContainer = Color(0xFF5C1F28), onErrorContainer = Color(0xFFFFDAD9)
        ),
        content = content
    )
}

@Composable
fun App(st: AppState) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf("chat") }
    var panel by remember { mutableStateOf(false) }
    var tutorial by remember { mutableStateOf(!st.anyActive()) }

    BackHandler(enabled = panel || screen == "settings") {
        if (panel) panel = false else screen = "chat"
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = screen == "chat",
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(20.dp)
                )
                Button(
                    onClick = { st.newChat(); screen = "chat"; scope.launch { drawer.close() } },
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.new_chat))
                }
                Text(
                    stringResource(R.string.recent),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
                )
                LazyColumn(Modifier.weight(1f)) {
                    items(st.chats.toList(), key = { it.id }) { c ->
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    if (c.title.isBlank()) stringResource(R.string.new_chat) else c.title,
                                    maxLines = 1
                                )
                            },
                            selected = c === st.current,
                            onClick = { st.current = c; screen = "chat"; scope.launch { drawer.close() } },
                            badge = {
                                IconButton(onClick = { st.deleteChat(c) }) {
                                    Icon(Icons.Default.Delete, stringResource(R.string.delete_chat))
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.settings)) },
                    icon = { Icon(Icons.Default.Settings, null) },
                    selected = screen == "settings",
                    onClick = { screen = "settings"; scope.launch { drawer.close() } },
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            if (screen == "settings") {
                SettingsScreen(st, onBack = { screen = "chat" })
            } else {
                ChatScreen(st, onMenu = { scope.launch { drawer.open() } }, onPanel = { panel = true })
            }
            if (panel) {
                AiPanel(st, onClose = { panel = false }, onSettings = { panel = false; screen = "settings" })
            }
            if (tutorial) {
                TutorialDialog(onClose = { tutorial = false }, onSettings = { tutorial = false; screen = "settings" })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(st: AppState, onMenu: () -> Unit, onPanel: () -> Unit) {
    val chat = st.current
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()

    LaunchedEffect(chat.id, chat.msgs.size, st.busy) {
        val n = chat.msgs.size + if (st.busy) 1 else 0
        if (n > 0) list.animateScrollToItem(n - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (chat.title.isBlank()) stringResource(R.string.new_chat) else chat.title, maxLines = 1)
                },
                navigationIcon = { IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, null) } },
                actions = {
                    IconButton(onClick = onPanel) {
                        Icon(Icons.Default.Face, stringResource(R.string.ai_panel_title))
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!st.anyActive()) {
                Text(
                    stringResource(R.string.banner_nokeys),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(8.dp)
                )
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = list,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chat.msgs.toList()) { m -> Bubble(m) }
                if (st.busy) {
                    item {
                        Text(
                            stringResource(R.string.thinking),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.type_message)) },
                    maxLines = 5
                )
                IconButton(
                    enabled = !st.busy && input.isNotBlank(),
                    onClick = {
                        val t = input.trim()
                        input = ""
                        st.scope.launch { Core.handle(ctx, st, chat, t) }
                    }
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        stringResource(R.string.send),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun Bubble(m: Msg) {
    val me = m.role == "user"
    val sys = m.role == "sys"
    val bg = when {
        me -> Gold.copy(alpha = 0.18f)
        sys -> MaterialTheme.colorScheme.errorContainer
        else -> Panel2
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (me) Alignment.End else Alignment.Start) {
        if (m.role == "ai" && m.from.isNotBlank()) {
            Text(m.from, color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.widthIn(max = 320.dp).background(bg, RoundedCornerShape(14.dp)).padding(10.dp)) {
            SelectionContainer { Text(m.text, color = if (sys) MaterialTheme.colorScheme.onErrorContainer else Ink) }
        }
        if (m.note.isNotBlank()) {
            Text(stringResource(R.string.answered_by, m.note), fontSize = 11.sp, color = Ink.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun AiPanel(st: AppState, onClose: () -> Unit, onSettings: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
    ) {
        Surface(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(310.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.ai_panel_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.tap_model), fontSize = 12.sp, color = Ink.copy(alpha = 0.6f))
                Spacer(Modifier.height(12.dp))
                for (p in Prov.values()) {
                    val hasKey = st.key(p).isNotBlank()
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.title, fontWeight = FontWeight.Bold)
                            if (!hasKey) {
                                Text(stringResource(R.string.no_key), fontSize = 12.sp, color = Ink.copy(alpha = 0.6f))
                            } else {
                                Text(
                                    st.model(p) + "  ›",
                                    fontSize = 13.sp,
                                    color = Gold,
                                    modifier = Modifier.clickable { st.nextModel(p) }.padding(vertical = 2.dp)
                                )
                                Text(
                                    stringResource(R.string.used_tokens, st.used[p.id] ?: 0),
                                    fontSize = 12.sp,
                                    color = Ink.copy(alpha = 0.6f)
                                )
                                if (st.blocked[p.id] == true) {
                                    Text(
                                        stringResource(R.string.blocked),
                                        fontSize = 12.sp,
                                        color = Color(0xFFFF8A80)
                                    )
                                }
                            }
                        }
                        Switch(
                            checked = st.active(p),
                            enabled = hasKey,
                            onCheckedChange = { st.setEnabled(p, it) }
                        )
                    }
                    HorizontalDivider()
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.discuss_mode), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.discuss_hint), fontSize = 12.sp, color = Ink.copy(alpha = 0.6f))
                    }
                    Switch(checked = st.discuss, onCheckedChange = { st.updateDiscuss(it) })
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onSettings) { Text(stringResource(R.string.settings)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(st: AppState, onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.tutorial_text), fontSize = 13.sp)
            for (p in Prov.values()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(p.title, fontWeight = FontWeight.Bold, color = Gold)
                        OutlinedTextField(
                            value = st.key(p),
                            onValueChange = { st.setKey(p, it.trim()) },
                            label = { Text(stringResource(R.string.key_label)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = st.models[p.id] ?: p.defaultModel,
                            onValueChange = { st.setModel(p, it.trim()) },
                            label = { Text(stringResource(R.string.model_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(onClick = { uri.openUri(p.keyUrl) }) { Text(stringResource(R.string.get_key)) }
                    }
                }
            }
            Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.about_text), fontSize = 13.sp)
        }
    }
}

@Composable
fun TutorialDialog(onClose: () -> Unit, onSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.no_keys_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.no_keys_text))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.tutorial_text), fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onSettings) { Text(stringResource(R.string.how_to_get)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } }
    )
}
