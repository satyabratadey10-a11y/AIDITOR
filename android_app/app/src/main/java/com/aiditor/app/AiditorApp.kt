package com.aiditor.app

import android.app.Application

class AiditorApp : Application() {
    companion object {
        lateinit var instance: AiditorApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
