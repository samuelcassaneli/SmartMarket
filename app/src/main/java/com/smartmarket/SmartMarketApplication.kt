package com.smartmarket

import android.app.Application

class SmartMarketApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        BackgroundJobs.scheduleAll(this)
    }
}
