package com.example.di

import android.content.Context
import androidx.room.Room
import com.example.core.common.DefaultDispatcherProvider
import com.example.core.common.DispatcherProvider
import com.example.core.domain.*
import com.example.core.network.KtorLocalServer
import com.example.core.network.NsdDiscoveryManager
import com.example.core.network.WifiDirectManager
import com.example.core.storage.AppDatabase
import com.example.core.storage.FileSystemHelperImpl
import com.example.core.data.TransferRepositoryImpl
import com.example.core.data.WebShareRepositoryImpl

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
