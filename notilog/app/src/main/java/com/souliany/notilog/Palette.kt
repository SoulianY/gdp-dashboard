package com.souliany.notilog

import android.content.Context
import android.graphics.Color

/** 화면 색: 브리핑 종류 색(res/values/colors.xml)과 메시지 앱 배지 색. 색은 구분에만 쓴다. */
object Palette {
    private val KIND = mapOf(
        "noti" to R.color.kind_noti, "daily" to R.color.kind_daily, "news" to R.color.kind_news,
        "politics" to R.color.kind_politics, "community" to R.color.kind_community, "mail" to R.color.kind_mail,
    )

    fun kind(context: Context, kind: String): Int = context.getColor(KIND[kind] ?: R.color.muted)

    /** 앱별 배지 (배경, 글자). push-inbox 브리핑 페이지와 같다. */
    val APP = mapOf(
        "카카오톡" to (Color.parseColor("#FEE500") to Color.parseColor("#3A1D1D")),
        "메시지" to (Color.parseColor("#2FB36B") to Color.WHITE),
        "Teams" to (Color.parseColor("#5B5FC7") to Color.WHITE),
        "딩톡" to (Color.parseColor("#1E89FF") to Color.WHITE),
        "삼성 월렛" to (Color.parseColor("#1C2B4A") to Color.WHITE),
    )
}
