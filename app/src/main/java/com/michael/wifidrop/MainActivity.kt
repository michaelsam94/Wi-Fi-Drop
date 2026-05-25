package com.michael.wifidrop

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.michael.wifidrop.core.domain.*
import com.michael.wifidrop.feature.history.HistoryUiState
import com.michael.wifidrop.feature.history.HistoryViewModel
import com.michael.wifidrop.feature.receive.ReceiveUiState
import com.michael.wifidrop.feature.receive.ReceiveViewModel
import com.michael.wifidrop.feature.send.SendUiState
import com.michael.wifidrop.feature.send.SendViewModel
import com.michael.wifidrop.ui.theme.MyApplicationTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as WifiDropApplication).container

        setContent {
            MyApplicationTheme {
                val sendViewModel: SendViewModel = viewModel(
                    factory = SendViewModel.Factory(
                        context = this,
                        discoverDevices = container.discoverNearbyDevicesUseCase,
                        buildFolderTree = container.buildFolderTreeUseCase,
                        startSend = container.startSendUseCase,
                        startWebShare = container.startWebShareUseCase,
                        transferRepository = container.transferRepository,
                        dispatchers = container.dispatchers
                    )
                )

                val receiveViewModel: ReceiveViewModel = viewModel(
                    factory = ReceiveViewModel.Factory(
                        context = this,
                        discoveryRepository = container.discoveryRepository,
                        ktorServer = container.ktorServer,
                        transferDao = container.transferDao,
                        dispatchers = container.dispatchers
                    )
                )

                val historyViewModel: HistoryViewModel = viewModel(
                    factory = HistoryViewModel.Factory(
                        transferRepository = container.transferRepository,
                        transferDao = container.transferDao,
                        dispatchers = container.dispatchers
                    )
                )

                MainAppScreen(sendViewModel, receiveViewModel, historyViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    sendViewModel: SendViewModel,
    receiveViewModel: ReceiveViewModel,
    historyViewModel: HistoryViewModel
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }

    val sendState by sendViewModel.state.collectAsStateWithLifecycle()
    val receiveState by receiveViewModel.state.collectAsStateWithLifecycle()
    val historyState by historyViewModel.historyUiState.collectAsStateWithLifecycle()

    // Handle One-shot Events
    LaunchedEffect(Unit) {
        sendViewModel.events.collect { event ->
            when (event) {
                is com.michael.wifidrop.feature.send.SendUiEvent.ShowError -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
                is com.michael.wifidrop.feature.send.SendUiEvent.NavigateToProgress -> {
                    Toast.makeText(context, "Link started!", Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    // Permission states
    val requiredPermissions = remember {
        mutableListOf<String>().apply {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }

    var permissionsGranted by remember {
        mutableStateOf(requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Wi-Fi Drop",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Wi-Fi Drop",
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = "IP: ${sendState.localIp.ifEmpty { "Resolving..." }}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                windowInsets = WindowInsets.navigationBars,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.drawBehind {
                    drawLine(
                        color = Color(0xFFE1E2EC),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.AutoMirrored.Default.Send, contentDescription = "Send") },
                    label = { Text("Send", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.testTag("nav_send")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Download, contentDescription = "Receive") },
                    label = { Text("Receive", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.testTag("nav_receive")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.testTag("nav_history")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!permissionsGranted) {
                PermissionRequestOverlay(onAccept = {
                    launcher.launch(requiredPermissions.toTypedArray())
                })
            } else {
                when (selectedTab) {
                    0 -> SendScreen(sendViewModel, sendState)
                    1 -> ReceiveScreen(receiveViewModel, receiveState)
                    2 -> HistoryScreen(historyState)
                }
            }
        }
    }
}

@Composable
fun PermissionRequestOverlay(onAccept: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.SettingsSuggest,
            contentDescription = "Permissions Required",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Permissions Required",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Wi-Fi Drop needs Location and Nearby Devices permissions to seek out local peers without internet access.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onAccept,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("grant_permissions_button")
        ) {
            Text("Grant Permissions")
        }
    }
}

@Composable
fun LoadingOverlay(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(strokeWidth = 3.dp)
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SendScreen(
    viewModel: SendViewModel,
    state: SendUiState,
    animationsEnabled: Boolean = true,
) {
    val context = LocalContext.current

    // Launcher for SAF Single/Multiple Files
    val selectFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            viewModel.onFilesSelected(uris.map { it.toString() })
        }
    }

    // Launcher for Directories
    val selectDirectoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            viewModel.onFilesSelected(listOf(it.toString()))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(28.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Share Content",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Select single/multiple files or folder trees to host on your local server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { selectFilesLauncher.launch(arrayOf("*/*")) },
                            enabled = !state.isBusy,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("select_files_button")
                        ) {
                            Icon(Icons.Default.UploadFile, "Files", modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Files", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { selectDirectoryLauncher.launch(null) },
                            enabled = !state.isBusy,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("select_folder_button")
                        ) {
                            Icon(Icons.Default.Folder, "Folders", modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Folders", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (state.selectedItems.isNotEmpty()) {
            item {
                Text(
                    text = "Selected Items (${state.selectedItems.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            items(state.selectedItems) { item ->
                OutlinedCard(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (item is TransferItem.SingleFile) Icons.Default.Description else Icons.Default.FolderShared,
                            contentDescription = "Item type",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1.5f)) {
                            Text(
                                text = item.name,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (item is TransferItem.Folder) {
                                    "${formatBytes(item.sizeBytes)} (${item.entries.size} files)"
                                } else {
                                    formatBytes(item.sizeBytes)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        IconButton(
                            onClick = { viewModel.removeSelectedItem(item) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove item",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.onWebShareTapped() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("action_web_share")
                    ) {
                        Icon(Icons.Default.QrCode, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Web Share with Computers", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Web Share Live Info
        state.webShareSession?.let { session ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(28.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("web_share_card")
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Active Web Share",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "WEB",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        // Render Canvas QR Code
                        QrCodeDisplay(
                            text = session.shareUrl,
                            modifier = Modifier
                                .size(200.dp)
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = session.shareUrl,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Scan QR code or open URL on your PC web browser to download directly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        OutlinedButton(
                            onClick = { viewModel.stopWebShare() },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("stop_web_share_button")
                        ) {
                            Icon(Icons.Default.Cancel, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stop Web Share", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Active P2P Session Detail Card
        state.activeSession?.let { s ->
            item {
                ActiveTransferCard(session = s, onCancel = { viewModel.cancelActiveSession(s.id) }, animationsEnabled = animationsEnabled)
            }
        }

        // Show discovered devices
        if (state.selectedItems.isNotEmpty() && state.webShareSession == null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NEARBY DEVICES",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                        color = Color(0xFF44474E),
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "SCANNING",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF005AC1)
                        )
                        val scanAlpha = if (animationsEnabled) {
                            val infiniteTransition = rememberInfiniteTransition(label = "scanPulse")
                            infiniteTransition.animateFloat(
                                initialValue = 0.2f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ), label = "alpha"
                            ).value
                        } else {
                            1f
                        }
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF005AC1).copy(alpha = scanAlpha))
                        )
                    }
                }
            }

            if (state.nearbyDevices.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Scanning local networks for matching receivers...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(state.nearbyDevices) { device ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE1E2EC)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.onSendTapped(device) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFFD8E2FF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = null,
                                    tint = Color(0xFF001D36),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.displayName,
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color(0xFF1A1C1E)
                                )
                                Text(
                                    text = "IP: ${device.localIp}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF44474E)
                                )
                            }
                            Text(
                                text = "Connect",
                                color = Color(0xFF005AC1),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        // Space buffer at bottom
        item {
            Spacer(modifier = Modifier.height(36.dp))
        }
    }

        if (state.isBusy) {
            LoadingOverlay(
                message = state.processingMessage.ifBlank { "Working…" },
            )
        }
    }
}

@Composable
fun ReceiveScreen(
    viewModel: ReceiveViewModel,
    state: ReceiveUiState,
    animationsEnabled: Boolean = true,
) {
    var deviceNameInput by remember { mutableStateOf(Build.MODEL ?: "Receiver Node") }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(28.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Broadcast Local Node",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Make your device discoverable to matching Wi-Fi Drop senders.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = deviceNameInput,
                    onValueChange = { if (!state.isAdvertising) deviceNameInput = it },
                    label = { Text("Display Name") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("device_name_field"),
                    singleLine = true,
                    enabled = !state.isAdvertising
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (!state.isAdvertising) {
                    Button(
                        onClick = { viewModel.startAdvertising(deviceNameInput) },
                        enabled = !state.isBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("start_broadcast_button")
                    ) {
                        Icon(Icons.Default.WifiTethering, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Advertising", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { viewModel.stopAdvertising() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("stop_broadcast_button")
                    ) {
                        Icon(Icons.Default.WifiTetheringOff, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop Sharing Nodes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (state.isAdvertising) {
            Spacer(modifier = Modifier.height(16.dp))
            if (state.isDownloading) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(28.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Receiving Files",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF001D36)
                        )
                        Text(
                            text = "From ${state.senderName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF44474E)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = state.currentFileName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = "${formatBytes(state.bytesTransferred)} / ${formatBytes(state.bytesTotal)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF44474E)
                            )
                            Text(
                                text = "${(state.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF005AC1)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = state.progress.coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            } else if (state.currentFileName.startsWith("Download completed")) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Download Completed!",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Files saved in Downloads directory.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            } else {
                AppRadarAnimation(animationsEnabled = animationsEnabled)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "\"${state.registeredName}\" is now visible.",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Keep this screen open until the sender taps your device and transfers files.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

        if (state.isBusy) {
            LoadingOverlay(
                message = state.processingMessage.ifBlank { "Starting receiver…" },
            )
        }
    }
}

@Composable
fun AppRadarAnimation(animationsEnabled: Boolean = true) {
    val radius: Float
    val alpha: Float
    if (animationsEnabled) {
        val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
        radius = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 100f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "RadiusSweep"
        ).value
        alpha = infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "AlphaFade"
        ).value
    } else {
        radius = 70f
        alpha = 0.35f
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.size(200.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(
            color = primaryColor,
            radius = radius * 1.5f,
            center = center,
            alpha = alpha,
            style = Stroke(width = 4.dp.toPx())
        )
        drawCircle(
            color = primaryColor,
            radius = 20.dp.toPx(),
            center = center
        )
    }
}

@Composable
fun HistoryScreen(state: HistoryUiState) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.activeTransfers.isNotEmpty()) {
            item {
                Text(
                    text = "Active Transfers",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(state.activeTransfers) { s ->
                ActiveTransferCard(session = s, onCancel = {})
            }
        }

        item {
            Text(
                text = "History Logs",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (state.historicalTransfers.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = "No Logs",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "History is clean. Let's send some files!",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        items(state.historicalTransfers) { s ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (s.state == TransferState.COMPLETED) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (s.state == TransferState.COMPLETED) Color(0xFF137333) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = s.deviceName,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Box(
                            modifier = Modifier
                                .background(
                                    if (s.direction == TransferDirection.SEND) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (s.direction == TransferDirection.SEND) "SENT" else "RCVD",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (s.direction == TransferDirection.SEND) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${s.items.size} item(s) • ${formatBytes(s.bytesTotal)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Completed: ${Date(s.completedAt ?: s.startedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveTransferCard(
    session: TransferSession,
    onCancel: () -> Unit,
    animationsEnabled: Boolean = true,
) {
    val progress = if (session.bytesTotal > 0) {
        session.bytesTransferred.toFloat() / session.bytesTotal.toFloat()
    } else 0f
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(28.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_transfer_card")
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    val directionText = if (session.direction == TransferDirection.SEND) "Sending" else "Receiving"
                    Text(
                        text = "$directionText ${session.items.size} item(s)",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF001D36)
                    )
                    Text(
                        text = if (session.direction == TransferDirection.SEND) "To ${session.deviceName}" else "From ${session.deviceName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF44474E)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val (badgeText, badgeBg, badgeTextClr) = when (session.state) {
                        TransferState.CONNECTING -> Triple("CONNECTING", Color(0xFFE2E8F0), Color(0xFF475569))
                        TransferState.TRANSFERRING -> Triple("TRANSFERRING", Color(0xFFD8E2FF), Color(0xFF001D36))
                        TransferState.COMPLETED -> Triple("COMPLETED", Color(0xFFD1FAE5), Color(0xFF065F46))
                        TransferState.FAILED -> Triple("FAILED", Color(0xFFFEE2E2), Color(0xFF991B1B))
                        TransferState.CANCELLED -> Triple("CANCELLED", Color(0xFFF3F4F6), Color(0xFF374151))
                        TransferState.QUEUED -> Triple("QUEUED", Color(0xFFFEF3C7), Color(0xFF92400E))
                        TransferState.PAUSED -> Triple("PAUSED", Color(0xFFFFEDD5), Color(0xFF9A3412))
                    }

                    Box(
                        modifier = Modifier
                            .background(badgeBg, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeTextClr,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp)
                        )
                    }
                    
                    val isFinished = session.state == TransferState.COMPLETED || 
                                     session.state == TransferState.FAILED || 
                                     session.state == TransferState.CANCELLED

                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isFinished) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = if (isFinished) "Dismiss" else "Cancel Stream",
                            tint = if (isFinished) Color(0xFF059669) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Bar & Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "${formatBytes(session.bytesTransferred)} / ${formatBytes(session.bytesTotal)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF44474E)
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF005AC1)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = Color(0xFF005AC1),
                trackColor = Color(0xFFE1E2EC)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val pulseAlpha = if (animationsEnabled) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ), label = "alpha"
                        ).value
                    } else {
                        1f
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF005AC1).copy(alpha = pulseAlpha))
                    )
                    Text(
                        text = "${(session.speedBps / 1048576.0).toFixed(1)} MB/s",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        color = Color(0xFF44474E),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                val speedMbs = session.speedBps / 1048576.0
                val etaText = if (speedMbs > 0.05) {
                    val remainingBytes = session.bytesTotal - session.bytesTransferred
                    val remainingSeconds = (remainingBytes / (session.speedBps.coerceAtLeast(1L).toDouble())).toInt()
                    if (remainingSeconds < 60) "~$remainingSeconds seconds left" else "~${remainingSeconds / 60}m ${remainingSeconds % 60}s left"
                } else {
                    "Estimating..."
                }

                Text(
                    text = etaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF44474E)
                )
            }
        }
    }
}

@Composable
fun QrCodeDisplay(text: String, modifier: Modifier = Modifier) {
    val qrBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = text) {
        value = withContext(Dispatchers.Default) {
            try {
                val qrCodeWriter = QRCodeWriter()
                val bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 512, 512)
                val width = bitMatrix.width
                val height = bitMatrix.height
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        bitmap.setPixel(
                            x,
                            y,
                            if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE,
                        )
                    }
                }
                bitmap.asImageBitmap()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (qrBitmap == null) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
        } else {
            Image(
                bitmap = qrBitmap!!,
                contentDescription = "QR code",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1] + ""
    return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}

fun Double.toFixed(digits: Int): String = String.format("%.${digits}f", this)
