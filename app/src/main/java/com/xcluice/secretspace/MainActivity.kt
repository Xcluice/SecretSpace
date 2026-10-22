package com.xcluice.secretspace

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var spaceOpen by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme { Root(spaceOpen) { spaceOpen = it } }
        }
    }

    override fun onResume() {
        super.onResume()
        Lock.suppress = false
    }

    override fun onStop() {
        super.onStop()
        // Auto-lock: leaving the app closes the hidden space.
        if (!Lock.suppress) spaceOpen = false
    }
}

@Composable
fun Root(spaceOpen: Boolean, setSpace: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val act = ctx as Activity
    var notes by remember { mutableStateOf(Store.load(ctx)) }
    var editing by remember { mutableStateOf<Note?>(null) }

    // Hidden space: no screenshots, blank preview in recents.
    DisposableEffect(spaceOpen) {
        if (spaceOpen) act.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { act.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    fun persist(list: List<Note>) {
        notes = list.sortedByDescending { it.updated }
        Store.save(ctx, notes)
    }

    Box(Modifier.fillMaxSize().background(GradBg)) {
        when {
            spaceOpen -> SpaceScreen(onClose = { setSpace(false) })
            editing != null -> Editor(
                note = editing!!,
                onSave = { n ->
                    persist(notes.filter { it.id != n.id } + n)
                    editing = null
                },
                onDelete = { id ->
                    persist(notes.filter { it.id != id })
                    editing = null
                },
                onSecret = { id ->
                    persist(notes.filter { it.id != id })
                    editing = null
                    setSpace(true)
                }
            )
            else -> NotesList(notes) { editing = it }
        }
    }
}

@Composable
fun NotesList(notes: List<Note>, open: (Note) -> Unit) {
    val fmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text(
                "Notes", fontSize = 30.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 18.dp, bottom = 14.dp)
            )
            if (notes.isEmpty()) {
                Text("No notes yet. Tap + to write one.", color = Color(0x99FFFFFF))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(notes, key = { it.id }) { n ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Panel)
                            .clickable { open(n) }
                            .padding(16.dp)
                    ) {
                        Text(
                            n.title.ifBlank { "Untitled" }, fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (n.body.isNotBlank()) {
                            Text(
                                n.body, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                color = Color(0xB3FFFFFF), modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Text(
                            fmt.format(Date(n.updated)), fontSize = 12.sp,
                            color = Color(0x80FFFFFF), modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                item { Spacer(Modifier.size(90.dp)) }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(58.dp)
                .clip(CircleShape)
                .background(GradMain)
                .clickable { open(Note(UUID.randomUUID().toString(), "", "", System.currentTimeMillis())) },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = "New note")
        }
    }
}

@Composable
fun Editor(
    note: Note,
    onSave: (Note) -> Unit,
    onDelete: (String) -> Unit,
    onSecret: (String) -> Unit
) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(note.title) }
    var body by remember { mutableStateOf(note.body) }

    fun finish() {
        if (title.isBlank() && body.isBlank()) onDelete(note.id)
        else onSave(note.copy(title = title, body = body, updated = System.currentTimeMillis()))
    }
    BackHandler { finish() }

    val clear = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent
    )

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { finish() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onDelete(note.id) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
        TextField(
            value = title,
            onValueChange = {
                title = it
                if (it.trim().equals(Store.secret(ctx), ignoreCase = true)) onSecret(note.id)
            },
            placeholder = { Text("Title", fontSize = 22.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White),
            singleLine = true,
            colors = clear,
            modifier = Modifier.fillMaxWidth()
        )
        TextField(
            value = body,
            onValueChange = { body = it },
            placeholder = { Text("Start typing") },
            colors = clear,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}
