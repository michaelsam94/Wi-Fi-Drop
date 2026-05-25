package com.michael.wifidrop

import android.app.Application
import com.michael.wifidrop.di.AppContainer

class WifiDropApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
