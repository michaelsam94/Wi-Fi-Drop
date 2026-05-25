package com.michael.wifidrop.playstore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.michael.wifidrop.HistoryScreen
import com.michael.wifidrop.ReceiveScreen
import com.michael.wifidrop.SendScreen
import com.michael.wifidrop.feature.history.HistoryUiState
import com.michael.wifidrop.feature.receive.ReceiveUiState
import com.michael.wifidrop.feature.send.SendUiState
import com.michael.wifidrop.feature.receive.ReceiveViewModel
import com.michael.wifidrop.feature.send.SendViewModel
import com.michael.wifidrop.ui.theme.MyApplicationTheme

enum class PlayStoreScene {
  SendWithDevices,
  ReceiveAdvertising,
  WebShare,
  History,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayStoreScreenshotFrame(
  scene: PlayStoreScene,
  sendViewModel: SendViewModel,
  receiveViewModel: ReceiveViewModel,
) {
  val fixtures = PlayStoreTestFixtures

  val selectedTab = when (scene) {
    PlayStoreScene.SendWithDevices, PlayStoreScene.WebShare -> 0
    PlayStoreScene.ReceiveAdvertising -> 1
    PlayStoreScene.History -> 2
  }

  val sendState = when (scene) {
    PlayStoreScene.WebShare -> fixtures.sendWebShare()
    PlayStoreScene.SendWithDevices -> fixtures.sendWithDevices()
    else -> fixtures.sendWithDevices()
  }

  val receiveState = when (scene) {
    PlayStoreScene.ReceiveAdvertising -> fixtures.receiveAdvertising()
    else -> ReceiveUiState(localIp = "192.168.1.42")
  }

  val historyState = when (scene) {
    PlayStoreScene.History -> fixtures.historyPopulated()
    else -> HistoryUiState()
  }

  val localIp = when (scene) {
    PlayStoreScene.ReceiveAdvertising -> receiveState.localIp
    else -> sendState.localIp
  }

  MyApplicationTheme {
    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(start = 4.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Wi-Fi Drop",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = "Wi-Fi Drop",
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
              )
            }
          },
          actions = {
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
              shape = RoundedCornerShape(20.dp),
              modifier = Modifier.padding(end = 12.dp),
            ) {
              Text(
                text = "IP: ${localIp.ifEmpty { "192.168.1.42" }}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
              )
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
          ),
        )
      },
      bottomBar = {
        NavigationBar(
          containerColor = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier.drawBehind {
            drawLine(
              color = Color(0xFFE1E2EC),
              start = Offset(0f, 0f),
              end = Offset(size.width, 0f),
              strokeWidth = 1.dp.toPx(),
            )
          },
        ) {
          NavigationBarItem(
            selected = selectedTab == 0,
            onClick = {},
            icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") },
            label = {
              Text(
                "Send",
                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
              )
            },
          )
          NavigationBarItem(
            selected = selectedTab == 1,
            onClick = {},
            icon = { Icon(Icons.Default.Download, contentDescription = "Receive") },
            label = {
              Text(
                "Receive",
                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
              )
            },
          )
          NavigationBarItem(
            selected = selectedTab == 2,
            onClick = {},
            icon = { Icon(Icons.Default.History, contentDescription = "History") },
            label = {
              Text(
                "History",
                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
              )
            },
          )
        }
      },
    ) { innerPadding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .background(MaterialTheme.colorScheme.background),
      ) {
        when (selectedTab) {
          0 -> SendScreen(sendViewModel, sendState, animationsEnabled = false)
          1 -> ReceiveScreen(receiveViewModel, receiveState, animationsEnabled = false)
          2 -> HistoryScreen(historyState)
        }
      }
    }
  }
}
