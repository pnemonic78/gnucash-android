package org.gnucash.android.ui.common

import android.app.ProgressDialog
import android.content.Context

class GnucashProgressDialog(context: Context) : ProgressDialog(context) {
    init {
        isIndeterminate = true
        setProgressStyle(STYLE_HORIZONTAL)
        setProgressNumberFormat(null)
        setProgressPercentFormat(null)
    }
}