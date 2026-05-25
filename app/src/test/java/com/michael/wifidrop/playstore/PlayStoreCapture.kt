package com.michael.wifidrop.playstore

import androidx.compose.runtime.Composable
import com.michael.wifidrop.R
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.activityTheme
import com.github.takahirom.roborazzi.captureRoboImage

private val playStoreCaptureOptions = RoborazziOptions(
  captureType = RoborazziOptions.CaptureType.Screenshot(),
)

@OptIn(ExperimentalRoborazziApi::class)
fun capturePlayStoreImage(
  outputPath: String,
  content: @Composable () -> Unit,
) {
  captureRoboImage(
    filePath = "../play-store/$outputPath",
    roborazziOptions = playStoreCaptureOptions,
    roborazziComposeOptions = RoborazziComposeOptions {
      activityTheme(R.style.Theme_MyApplication)
    },
    content = content,
  )
}
