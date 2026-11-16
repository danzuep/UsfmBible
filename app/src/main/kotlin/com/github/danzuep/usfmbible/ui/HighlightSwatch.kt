package com.github.danzuep.usfmbible.ui

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import com.github.danzuep.usfmbible.R
import com.github.danzuep.usfmbible.core.Highlight

private const val SWATCH = "\u2003\u2003\u2003\u2003"

fun Context.highlightSwatch(color: Int): CharSequence =
    if (color == Highlight.COLOR_NONE) {
        getString(R.string.text_highlight_color_none)
    } else {
        SpannableString(SWATCH).apply { setSpan(BackgroundColorSpan(color), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    }
