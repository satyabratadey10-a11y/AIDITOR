package com.aiditor.app

import android.app.Application

class AiditorApp : Application() {
    companion object {
        var instance: AiditorApp? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.aiditor.app.util.CrashHandler.init(this)
    }
}
