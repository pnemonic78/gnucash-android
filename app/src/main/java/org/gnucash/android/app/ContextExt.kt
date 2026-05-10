package org.gnucash.android.app

import android.content.Context
import android.content.res.Configuration

val Context.isNightMode: Boolean get() = resources.configuration.isNightMode

val Configuration.isNightMode: Boolean
    get() {
        val nightMode = uiMode and Configuration.UI_MODE_NIGHT_MASK
        return (nightMode == Configuration.UI_MODE_NIGHT_YES)
    }

val Context.isLandscape: Boolean get() = resources.configuration.isLandscape

// Width is at least 25% wider than height.
val Configuration.isLandscape: Boolean
    get() {
        val width = screenWidthDp
        val height = screenHeightDp
        return (width * 4) >= (height * 5)
    }
