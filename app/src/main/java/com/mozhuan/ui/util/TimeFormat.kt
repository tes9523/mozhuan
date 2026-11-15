package com.mozhuan.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDateTime(timeMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timeMillis))
