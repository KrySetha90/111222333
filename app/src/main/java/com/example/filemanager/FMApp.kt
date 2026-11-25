package com.example.filemanager

import android.app.Application

class FMApp : Application() {
    override fun onCreate() { super.onCreate(); U.ctx = this }
}
