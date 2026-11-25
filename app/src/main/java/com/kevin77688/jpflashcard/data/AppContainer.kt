package com.kevin77688.jpflashcard.data

import android.content.Context

/** Hand-rolled dependency container; the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    val auth = AuthRepository()
    val speaker = Speaker(context)
}
