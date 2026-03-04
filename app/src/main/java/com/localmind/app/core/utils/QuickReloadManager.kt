package com.localmind.app.core.utils

import android.app.Activity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickReloadManager @Inject constructor() {
    fun reload(activity: Activity) {
        activity.runOnUiThread {
            activity.recreate()
        }
    }
}

