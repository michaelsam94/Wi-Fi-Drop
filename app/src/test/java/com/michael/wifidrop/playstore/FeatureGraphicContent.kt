package com.michael.wifidrop.playstore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michael.wifidrop.ui.theme.M3OnPrimary
import com.michael.wifidrop.ui.theme.M3Primary
import com.michael.wifidrop.ui.theme.M3PrimaryContainer
import com.michael.wifidrop.ui.theme.MyApplicationTheme

@Composable
fun FeatureGraphicContent() {
  MyApplicationTheme {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.horizontalGradient(
            colors = listOf(
              M3Primary,
              Color(0xFF003D8F),
              Color(0xFF001D36),
            ),
          ),
        ),
    ) {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 48.dp, vertical = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Share,
              contentDescription = null,
              tint = M3OnPrimary,
              modifier = Modifier.size(40.dp),
            )
            Text(
              text = "Wi-Fi Drop",
              color = M3OnPrimary,
              fontWeight = FontWeight.Bold,
              fontSize = 42.sp,
            )
          }
          Text(
            text = "Share files locally — no internet required",
            color = M3PrimaryContainer,
            fontWeight = FontWeight.Medium,
            fontSize = 22.sp,
            lineHeight = 28.sp,
          )
          Text(
            text = "P2P transfers • Web share • Transfer history",
            color = M3OnPrimary.copy(alpha = 0.85f),
            fontSize = 16.sp,
          )
        }

        Box(
          modifier = Modifier
            .width(220.dp)
            .height(400.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(8.dp),
          contentAlignment = Alignment.Center,
        ) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .clip(RoundedCornerShape(18.dp))
              .background(Color.White),
          ) {
            PlayStoreMiniPhonePreview()
          }
        }
      }
    }
  }
}

@Composable
private fun PlayStoreMiniPhonePreview() {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = Icons.Default.Share,
          contentDescription = null,
          tint = M3Primary,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = "Wi-Fi Drop",
          fontWeight = FontWeight.SemiBold,
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onBackground,
        )
      }
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(120.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(M3PrimaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "Nearby devices\nready to connect",
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
          fontSize = 10.sp,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
    }
  }
}
