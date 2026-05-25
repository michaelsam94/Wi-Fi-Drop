package com.michael.wifidrop.di

import android.content.Context
import androidx.room.Room
import com.michael.wifidrop.core.common.DefaultDispatcherProvider
import com.michael.wifidrop.core.common.DispatcherProvider
import com.michael.wifidrop.core.domain.*
import com.michael.wifidrop.core.network.KtorLocalServer
import com.michael.wifidrop.core.network.NsdDiscoveryManager
import com.michael.wifidrop.core.network.WifiDirectManager
import com.michael.wifidrop.core.storage.AppDatabase
import com.michael.wifidrop.core.storage.FileSystemHelperImpl
import com.michael.wifidrop.core.data.TransferRepositoryImpl
import com.michael.wifidrop.core.data.WebShareRepositoryImpl

class AppContainer(private val context: Context) {

    val dispatchers: DispatcherProvider = DefaultDispatcherProvider()

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "wifidrop.db"
        ).fallbackToDestructiveMigration().build()
    }

    val transferDao by lazy { database.transferDao() }

    val fileSystemHelper: FileSystemHelper by lazy {
        FileSystemHelperImpl(context.applicationContext, dispatchers)
    }

    val fileValidator by lazy { FileValidator() }

    val ktorServer: KtorLocalServer by lazy {
        KtorLocalServer(context.applicationContext, dispatchers)
    }

    val discoveryRepository: DiscoveryRepository by lazy {
        NsdDiscoveryManager(context.applicationContext, dispatchers).apply {
            setServerPort(33445) // Wi-Fi Drop dedicated port
        }
    }

    val wifiDirectManager: WifiDirectManager by lazy {
        WifiDirectManager(context.applicationContext, dispatchers)
    }

    val transferRepository: TransferRepository by lazy {
        TransferRepositoryImpl(context.applicationContext, ktorServer, transferDao, dispatchers)
    }

    val webShareRepository: WebShareRepository by lazy {
        WebShareRepositoryImpl(ktorServer, dispatchers)
    }

    val startSendUseCase by lazy {
        StartSendUseCase(transferRepository, fileValidator)
    }

    val observeLiveTransferUseCase by lazy {
        ObserveLiveTransferUseCase(transferRepository)
    }

    val buildFolderTreeUseCase by lazy {
        BuildFolderTreeUseCase(fileSystemHelper)
    }

    val startWebShareUseCase by lazy {
        StartWebShareUseCase(webShareRepository)
    }

    val discoverNearbyDevicesUseCase by lazy {
        DiscoverNearbyDevicesUseCase(discoveryRepository)
    }
}
