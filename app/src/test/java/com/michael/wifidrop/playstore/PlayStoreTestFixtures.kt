package com.michael.wifidrop.playstore

import com.michael.wifidrop.core.domain.*
import com.michael.wifidrop.feature.history.HistoryUiState
import com.michael.wifidrop.feature.receive.ReceiveUiState
import com.michael.wifidrop.feature.send.SendUiState
import java.util.UUID

object PlayStoreTestFixtures {

  private const val DEMO_IP = "192.168.1.42"

  val demoFiles = listOf(
    TransferItem.SingleFile("content://demo/vacation.mp4", "Vacation_Clip.mp4", 524_288_000L),
    TransferItem.SingleFile("content://demo/photos.zip", "Family_Photos.zip", 89_478_400L),
    TransferItem.Folder(
      uriString = "content://demo/tree/project",
      name = "Project_Assets",
      entries = listOf(
        FolderEntry("designs/logo.png", 2_048_000L),
        FolderEntry("designs/banner.jpg", 4_096_000L),
        FolderEntry("docs/readme.pdf", 512_000L),
      ),
    ),
  )

  val demoDevices = listOf(
    DiscoveredDevice(
      id = "device-1",
      displayName = "Alex's Pixel",
      localIp = "192.168.1.18",
      port = 33445,
      sharePort = 8080,
      supportsClient = true,
    ),
    DiscoveredDevice(
      id = "device-2",
      displayName = "Living Room Tablet",
      localIp = "192.168.1.55",
      port = 33445,
      sharePort = 8080,
      supportsClient = true,
    ),
  )

  val demoWebShare = WebShareSession(
    localIp = DEMO_IP,
    port = 8080,
    shareUrl = "http://$DEMO_IP:8080/share/a8f3c2",
  )

  val demoActiveTransfer = TransferSession(
    id = UUID.fromString("00000000-0000-4000-8000-000000000001"),
    deviceName = "Alex's Pixel",
    items = demoFiles.take(2),
    direction = TransferDirection.SEND,
    state = TransferState.TRANSFERRING,
    bytesTotal = 524_288_000L,
    bytesTransferred = 314_572_800L,
    speedBps = 12_582_912L,
    startedAt = System.currentTimeMillis() - 45_000L,
  )

  val demoHistory = listOf(
    TransferSession(
      id = UUID.fromString("00000000-0000-4000-8000-000000000010"),
      deviceName = "Alex's Pixel",
      items = listOf(demoFiles[0]),
      direction = TransferDirection.SEND,
      state = TransferState.COMPLETED,
      bytesTotal = 524_288_000L,
      bytesTransferred = 524_288_000L,
      speedBps = 0L,
      startedAt = System.currentTimeMillis() - 86_400_000L,
      completedAt = System.currentTimeMillis() - 86_350_000L,
    ),
    TransferSession(
      id = UUID.fromString("00000000-0000-4000-8000-000000000011"),
      deviceName = "Living Room Tablet",
      items = listOf(demoFiles[1]),
      direction = TransferDirection.RECEIVE,
      state = TransferState.COMPLETED,
      bytesTotal = 89_478_400L,
      bytesTransferred = 89_478_400L,
      speedBps = 0L,
      startedAt = System.currentTimeMillis() - 172_800_000L,
      completedAt = System.currentTimeMillis() - 172_760_000L,
    ),
    TransferSession(
      id = UUID.fromString("00000000-0000-4000-8000-000000000012"),
      deviceName = "Work Laptop",
      items = listOf(demoFiles[2]),
      direction = TransferDirection.SEND,
      state = TransferState.COMPLETED,
      bytesTotal = 6_656_000L,
      bytesTransferred = 6_656_000L,
      speedBps = 0L,
      startedAt = System.currentTimeMillis() - 604_800_000L,
      completedAt = System.currentTimeMillis() - 604_750_000L,
    ),
  )

  fun sendWithDevices(): SendUiState = SendUiState(
    selectedItems = demoFiles,
    nearbyDevices = demoDevices,
    localIp = DEMO_IP,
  )

  fun sendWebShare(): SendUiState = SendUiState(
    selectedItems = demoFiles.take(2),
    webShareSession = demoWebShare,
    localIp = DEMO_IP,
  )

  fun receiveAdvertising(): ReceiveUiState = ReceiveUiState(
    isAdvertising = true,
    registeredName = "My Phone",
    localIp = DEMO_IP,
  )

  fun historyPopulated(): HistoryUiState = HistoryUiState(
    activeTransfers = emptyList(),
    historicalTransfers = demoHistory,
  )
}
