package com.example

import android.app.Application
import com.example.di.AppContainer

class WifiDropApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
