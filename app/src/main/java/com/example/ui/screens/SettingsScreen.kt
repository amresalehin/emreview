package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.VisionModel
import com.example.ui.GalleryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: GalleryViewModel,
    modifier: Modifier = Modifier,
    initialTab: Int = 0
) {
    var selectedTab by remember { mutableStateOf(initialTab) }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Settings Header
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Cloud backup and AI vision integration",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Material 3 Tabs
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth().testTag("settings_tab_row"),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Cloud Backup", fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        modifier = Modifier.testTag("settings_tab_cloud")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("AI Setup", fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        modifier = Modifier.testTag("settings_tab_ai")
                    )
                }
            }
        }

        // Tab Content Container
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            when (selectedTab) {
                0 -> SyncScreen(viewModel = viewModel)
                1 -> AiSetupContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun AiSetupContent(
    viewModel: GalleryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var aiBaseUrl by remember { mutableStateOf("") }
    var aiApiKey by remember { mutableStateOf("") }
    var aiModelId by remember { mutableStateOf("") }
    var aiModels by remember { mutableStateOf<List<VisionModel>>(emptyList()) }
    var aiDiscoveryError by remember { mutableStateOf<String?>(null) }
    var aiDiscovering by remember { mutableStateOf(false) }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val config = viewModel.getAiProviderConfig()
        aiBaseUrl = config.baseUrl
        aiApiKey = config.apiKey
        aiModelId = config.modelId
    }

    val isConfigured = aiBaseUrl.isNotBlank() && aiApiKey.isNotBlank() && aiModelId.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth().testTag("ai_status_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isConfigured) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            ),
            border = BorderStroke(
                1.dp,
                if (isConfigured) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isConfigured) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondary
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isConfigured) "AI Vision Active & Ready" else "AI Vision Setup Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isConfigured) "Selected Model: $aiModelId"
                        else "Configure an OpenAI-compatible vision provider to enable automatic smart tags and image descriptions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Quick Presets
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "PROVIDER PRESETS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = {
                            aiBaseUrl = "https://api.openai.com/v1"
                            aiModelId = "gpt-4o-mini"
                        },
                        label = { Text("OpenAI", fontSize = 12.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            aiBaseUrl = "https://api.groq.com/openai/v1"
                            aiModelId = "llama-3.2-11b-vision-preview"
                        },
                        label = { Text("Groq", fontSize = 12.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            aiBaseUrl = "https://openrouter.ai/api/v1"
                        },
                        label = { Text("OpenRouter", fontSize = 12.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            aiBaseUrl = "http://10.0.2.2:11434/v1"
                            aiModelId = "llava"
                        },
                        label = { Text("Ollama", fontSize = 12.sp) }
                    )
                }
            }
        }

        // Configuration Inputs Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "CONNECTION SETTINGS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp
                )

                OutlinedTextField(
                    value = aiBaseUrl,
                    onValueChange = { aiBaseUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                    trailingIcon = {
                        if (aiBaseUrl.isNotEmpty()) {
                            IconButton(onClick = { aiBaseUrl = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("ai_base_url_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = aiApiKey,
                    onValueChange = { aiApiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                            Icon(
                                imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isApiKeyVisible) "Hide key" else "Show key"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("ai_api_key_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation()
                )

                OutlinedTextField(
                    value = aiModelId,
                    onValueChange = { aiModelId = it },
                    label = { Text("Vision Model ID") },
                    placeholder = { Text("gpt-4o-mini, llama-3.2-11b-vision-preview...") },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    trailingIcon = {
                        if (aiModelId.isNotEmpty()) {
                            IconButton(onClick = { aiModelId = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("ai_model_id_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Discover Models Action Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        enabled = aiBaseUrl.isNotBlank() && aiApiKey.isNotBlank() && !aiDiscovering,
                        onClick = {
                            coroutineScope.launch {
                                aiDiscovering = true
                                aiDiscoveryError = null
                                val result = viewModel.discoverAiModels(aiBaseUrl, aiApiKey)
                                result.fold(
                                    onSuccess = { models ->
                                        aiModels = models
                                        if (models.isNotEmpty() && aiModelId.isBlank()) {
                                            aiModelId = models.first().id
                                        }
                                        Toast.makeText(context, "Discovered ${models.size} models", Toast.LENGTH_SHORT).show()
                                    },
                                    onFailure = {
                                        aiDiscoveryError = it.message ?: "Model discovery failed"
                                        Toast.makeText(context, "Discovery failed: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                )
                                aiDiscovering = false
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("ai_discover_models_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (aiDiscovering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Discovering…")
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Discover Models")
                        }
                    }

                    if (aiModels.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { aiModels = emptyList() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Reset")
                        }
                    }
                }

                aiDiscoveryError?.let {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(it, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Discovered Models Picker
                if (aiModels.isNotEmpty()) {
                    Text(
                        "Discovered Vision Models (${aiModels.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        aiModels.forEach { model ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { aiModelId = model.id },
                                shape = RoundedCornerShape(12.dp),
                                color = if (model.id == aiModelId) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = if (model.id == aiModelId)
                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = model.name,
                                            fontWeight = if (model.id == aiModelId) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (model.capabilityKnown) "Verified vision capability" else "Capability unverified",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (model.capabilityKnown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (model.id == aiModelId) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Save Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        enabled = aiBaseUrl.isNotBlank() && aiApiKey.isNotBlank() && aiModelId.isNotBlank(),
                        onClick = {
                            viewModel.saveAiProviderConfig(aiBaseUrl, aiApiKey, aiModelId)
                            Toast.makeText(context, "AI settings saved successfully!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("save_ai_config_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Configuration")
                    }
                }
            }
        }

        // AI Album Automation Card
        val autoAssignAlbumsOnAi by viewModel.autoAssignAlbumsOnAi.collectAsState()
        val isAiUpdatingAlbums by viewModel.isAiUpdatingAlbums.collectAsState()
        val aiAlbumProgress by viewModel.aiAlbumProgress.collectAsState()
        val aiAlbumStatus by viewModel.aiAlbumStatus.collectAsState()

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_album_automation_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Smart Album Automation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Auto-categorize photos into smart collections",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Auto-Assign Switch Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Auto-assign albums on AI analysis",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "When photos are tagged by AI, automatically add them to matching smart albums (Nature, People, Food, Travel, etc.)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoAssignAlbumsOnAi,
                        onCheckedChange = { viewModel.setAutoAssignAlbumsOnAi(it) },
                        modifier = Modifier.testTag("settings_switch_auto_assign_albums")
                    )
                }

                // Batch organize action
                if (isAiUpdatingAlbums) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val frac = if (aiAlbumProgress.second > 0) aiAlbumProgress.first.toFloat() / aiAlbumProgress.second.toFloat() else 0f
                        LinearProgressIndicator(
                            progress = { frac },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = aiAlbumStatus ?: "Organizing photos...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(
                                onClick = { viewModel.cancelAiAlbumUpdate() },
                                modifier = Modifier.testTag("settings_btn_cancel_ai_albums")
                            ) {
                                Text("Cancel", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = { viewModel.autoUpdateAlbumsWithAi() },
                        modifier = Modifier.fillMaxWidth().testTag("settings_btn_ai_auto_update_albums"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Auto-Update All Albums with AI", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
