package com.michael.wifidrop.playstore

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val PHONE = "w360dp-h640dp-xxhdpi"
private const val TABLET = "w800dp-h1280dp-xhdpi"

@RunWith(RobolectricTestRunner::class)
@Category(PlayStoreScreenshotTests::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PlayStoreScreenshotTest {

  private val app: Application
    get() = ApplicationProvider.getApplicationContext()

  @Test
  @Config(qualifiers = PHONE)
  fun phone_01_dashboard() {
    capturePlayStoreImage("phone/01_dashboard.png") {
      PlayStoreScreenshotFrame(
        scene = PlayStoreScene.SendWithDevices,
        sendViewModel = createPlayStoreSendViewModel(app),
        receiveViewModel = createPlayStoreReceiveViewModel(app),
      )
    }
  }

  @Test
  @Config(qualifiers = PHONE)
  fun phone_02_receive() {
    capturePlayStoreImage("phone/02_receive.png") {
      PlayStoreScreenshotFrame(
        scene = PlayStoreScene.ReceiveAdvertising,
        sendViewModel = createPlayStoreSendViewModel(app),
        receiveViewModel = createPlayStoreReceiveViewModel(app),
      )
    }
  }

  @Test
  @Config(qualifiers = PHONE)
  fun phone_03_web_share() {
    capturePlayStoreImage("phone/03_web_share.png") {
      PlayStoreScreenshotFrame(
        scene = PlayStoreScene.WebShare,
        sendViewModel = createPlayStoreSendViewModel(app),
        receiveViewModel = createPlayStoreReceiveViewModel(app),
      )
    }
  }

  @Test
  @Config(qualifiers = PHONE)
  fun phone_04_history() {
    capturePlayStoreImage("phone/04_history.png") {
      PlayStoreScreenshotFrame(
        scene = PlayStoreScene.History,
        sendViewModel = createPlayStoreSendViewModel(app),
        receiveViewModel = createPlayStoreReceiveViewModel(app),
      )
    }
  }

  @Test
  @Config(qualifiers = TABLET)
  fun tablet_01_dashboard() {
    capturePlayStoreImage("tablet/01_dashboard.png") {
      PlayStoreScreenshotFrame(
        scene = PlayStoreScene.SendWithDevices,
        sendViewModel = createPlayStoreSendViewModel(app),
        receiveViewModel = createPlayStoreReceiveViewModel(app),
      )
    }
  }

  @Test
  @Config(qualifiers = TABLET)
  fun tablet_02_receive() {
    capturePlayStoreImage("tablet/02_receive.png") {
      PlayStoreScreenshotFrame(
        scene = PlayStoreScene.ReceiveAdvertising,
        sendViewModel = createPlayStoreSendViewModel(app),
        receiveViewModel = createPlayStoreReceiveViewModel(app),
      )
    }
  }

  @Test
  @Config(qualifiers = TABLET)
  fun tablet_03_web_share() {
    capturePlayStoreImage("tablet/03_web_share.png") {
      PlayStoreScreenshotFrame(
        scene = PlayStoreScene.WebShare,
        sendViewModel = createPlayStoreSendViewModel(app),
        receiveViewModel = createPlayStoreReceiveViewModel(app),
      )
    }
  }

  @Test
  @Config(qualifiers = TABLET)
  fun tablet_04_history() {
    capturePlayStoreImage("tablet/04_history.png") {
      PlayStoreScreenshotFrame(
        scene = PlayStoreScene.History,
        sendViewModel = createPlayStoreSendViewModel(app),
        receiveViewModel = createPlayStoreReceiveViewModel(app),
      )
    }
  }
}
