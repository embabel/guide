package com.embabel.guide.util

fun String.truncate(maxLength: Int = 120): String =
    if (length > maxLength) substring(0, maxLength) + "..." else this
