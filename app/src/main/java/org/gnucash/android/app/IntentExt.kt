package org.gnucash.android.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent

private const val UriModeFlags =
    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

@SuppressLint("WrongConstant")
fun Context.takePersistableUriPermission(intent: Intent) {
    val uri = intent.data ?: return
    val persistFlag = intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
    if (persistFlag == 0) return
    val modeFlags: Int = intent.flags and UriModeFlags
    if (modeFlags == 0) return
    contentResolver.takePersistableUriPermission(uri, modeFlags)
}