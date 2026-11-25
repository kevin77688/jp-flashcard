package com.kevin77688.jpflashcard

import android.app.Application
import com.kevin77688.jpflashcard.data.AppContainer

class JpFlashcardApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
