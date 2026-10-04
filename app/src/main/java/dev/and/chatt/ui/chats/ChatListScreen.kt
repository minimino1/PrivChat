package dev.and.chatt.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.and.chatt.data.Conversation
import dev.and.chatt.data.FirebaseProvider
import dev.and.chatt.ui.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onOpenConversation: (String) -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: ChatListViewModel = viewModel(),
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val myProfile by viewModel.myProfile.collectAsStateWithLifecycle()
    val myUid = FirebaseProvider.currentUid.orEmpty()

    LaunchedEffect(Unit) {
        viewModel.openConversationId.collect { id ->
            if (id != null) {
                viewModel.consumeNavigation()
                onOpenConversation(id)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chatt", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = onOpenProfile) {
                        Avatar(name = myProfile?.displayName.orEmpty(), size = 32)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openNewChat) {
                Icon(Icons.Default.Add, contentDescription = "Neuer Chat")
            }
        },
    ) { padding ->
        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Noch keine Chats.\nTippe auf + und starte eine Unterhaltung.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(conversations, key = { it.id }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        myUid = myUid,
                        onClick = { onOpenConversation(conversation.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (viewModel.showNewChat) {
        NewChatDialog(
            viewModel = viewModel,
            onDismiss = viewModel::closeNewChat,
        )
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    myUid: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name = conversation.title, highlighted = conversation.hasUnread)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.title.ifBlank { "Ohne Titel" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = Format.relative(conversation.lastMessageAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = Format.preview(conversation.lastMessageText, conversation.lastSenderId == myUid),
                style = MaterialTheme.typography.bodyMedium,
                color = if (conversation.hasUnread) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (conversation.hasUnread) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (conversation.hasUnread) {
            Spacer(Modifier.size(10.dp))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
fun Avatar(name: String, highlighted: Boolean = false, size: Int = 44) {
    Box(
        modifier = Modifier.size(size.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = if (highlighted) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
            shape = CircleShape,
            modifier = Modifier.size(size.dp),
        ) {}
        Text(
            text = Format.initials(name),
            style = MaterialTheme.typography.titleMedium,
            color = if (highlighted) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun NewChatDialog(
    viewModel: ChatListViewModel,
    onDismiss: () -> Unit,
) {
    val users by viewModel.users.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = viewModel::createGroup,
                enabled = viewModel.groupTitle.isNotBlank() && viewModel.selectedParticipants.isNotEmpty(),
            ) { Text("Gruppe erstellen") }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Schließen")
                }
            }
        },
        title = { Text("Neuer Chat") },
        text = {
            Column {
                OutlinedTextField(
                    value = viewModel.searchText,
                    onValueChange = viewModel::onSearchChange,
                    label = { Text("Person suchen") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))

                if (users.isEmpty() && viewModel.searchText.isNotBlank()) {
                    Text(
                        "Keine Treffer. Zum Testen ein zweites Konto anlegen.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(users, key = { it.uid }) { user ->
                        val selected = user in viewModel.selectedParticipants
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleParticipant(user) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { viewModel.toggleParticipant(user) },
                            )
                            Text(user.displayName, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }

                if (viewModel.selectedParticipants.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.selectedParticipants.firstOrNull()
                                ?.let(viewModel::startDirectChat)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(
                            if (viewModel.selectedParticipants.size == 1) "Direktchat starten"
                            else "${viewModel.selectedParticipants.size} ausgewählt"
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = viewModel.groupTitle,
                    onValueChange = viewModel::onGroupTitleChange,
                    label = { Text("Gruppenname (für Gruppe)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                viewModel.errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    )
}