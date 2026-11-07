package com.souliany.notilog

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil3.load
import com.google.android.material.R as M
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * 브리핑 한 건 보기. 열면 읽음으로 표시한다.
 * 메시지 영역(채팅방별 카드, 앱 색 띠·배지)과 알림·요약 영역(카드)으로 나누고, 제외 목록은 접어 둔다.
 */
class BriefingActivity : AppCompatActivity() {
    private val dp by lazy { resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = BriefingStore(this)
        val b = intent.getStringExtra(EXTRA_ID)?.let(store::get) ?: return finish()
        store.markRead(listOf(b.id))
        setContentView(R.layout.activity_briefing)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            title = Briefing.KINDS[b.kind] ?: b.kind
            setDisplayHomeAsUpEnabled(true)
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(12), px(16), px(32))
        }
        body.addView(text(b.title, M.attr.textAppearanceHeadlineSmall))
        body.addView(text(OffsetDateTime.parse(b.created).format(TIME) + if (b.summary.isNotEmpty()) "  ·  ${b.summary}" else "",
            M.attr.textAppearanceBodySmall, muted = true).pad(top = 4))

        val chats = b.sections.filter { it.chat }
        val rest = b.sections.filter { !it.chat }
        if (chats.isNotEmpty()) {
            body.addView(groupHeader("💬 메시지"))
            chats.forEach { body.addView(card(it)) }
        }
        if (rest.isNotEmpty()) {
            body.addView(groupHeader(if (b.kind == "daily") "📌 오늘 정리" else if (chats.isEmpty()) "📌 내용" else "📌 알림·요약"))
            rest.forEach { body.addView(card(it)) }
        }
        findViewById<ScrollView>(R.id.scroll).apply {
            addView(body)
            // 하단 내비게이션 바에 마지막 버튼이 가리지 않게
            ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
                v.setPadding(0, 0, 0, insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
                insets
            }
        }
    }

    /** 섹션 하나 = 카드 하나. 메시지 카드는 테두리를 앱 색으로, 제목 앞에 앱 배지, 줄마다 오른쪽에 시각. */
    private fun card(s: Briefing.Section): View {
        val color = APP_COLORS[s.app]
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(14), px(12), px(14), px(12))
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (s.app.isNotEmpty()) head.addView(badge(s.app, color))
        head.addView(text(s.title, M.attr.textAppearanceTitleSmall), LinearLayout.LayoutParams(0, -2, 1f))
        if (s.chat) head.addView(text("${s.items.size}개", M.attr.textAppearanceBodySmall, muted = true))
        inner.addView(head)

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = if (s.fold) View.GONE else View.VISIBLE }
        for (item in s.items) list.addView(row(item))
        inner.addView(list)
        if (s.fold) head.setOnClickListener { list.visibility = if (list.visibility == View.GONE) View.VISIBLE else View.GONE }

        return MaterialCardView(this, null, M.attr.materialCardViewOutlinedStyle).apply {
            addView(inner)
            if (s.chat && color != null) {
                strokeColor = color.first
                strokeWidth = px(2)
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(10) }
        }
    }

    /** 한 줄: (사진) + 본문 + (시각이면 오른쪽, 아니면 아래 회색 글씨) + 링크 버튼 */
    private fun row(item: Briefing.Item): View {
        val isTime = TIME_ONLY.matches(item.sub)
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, px(8), 0, 0)
            addView(text(item.text, M.attr.textAppearanceBodyMedium), LinearLayout.LayoutParams(0, -2, 1f))
            if (isTime) addView(text(item.sub, M.attr.textAppearanceBodySmall, muted = true).pad(left = 10))
        }
        if (isTime && item.link.isEmpty() && item.image.isEmpty()) return line
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            if (item.image.isNotEmpty()) addView(photo(item.image), LinearLayout.LayoutParams(-1, px(180)).apply { topMargin = px(10) })
            addView(line)
            if (!isTime && item.sub.isNotEmpty()) addView(text(item.sub, M.attr.textAppearanceBodySmall, muted = true).pad(top = 2))
            if (item.link.isNotEmpty()) addView(MaterialButton(context, null, M.attr.materialButtonOutlinedStyle).apply {
                text = if ("calendar.google.com" in item.link) "캘린더에 등록" else "원문 보기"
                setOnClickListener { open(item.link) }
            })
        }
    }

    /** 기사 사진. 못 받아 오면 자리를 비운다(웹 페이지와 같음). */
    private fun photo(url: String) = ImageView(this).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        clipToOutline = true
        background = GradientDrawable().apply { cornerRadius = px(8).toFloat(); color(Color.parseColor("#E6E9EE")) }
        load(url) { listener(onError = { _, _ -> visibility = View.GONE }) }
    }

    private fun badge(app: String, color: Pair<Int, Int>?) = TextView(this).apply {
        text = app
        textSize = 11f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(color?.second ?: Color.parseColor("#3A4350"))
        background = GradientDrawable().apply {
            cornerRadius = px(10).toFloat()
            color(color?.first ?: Color.parseColor("#E6E9EE"))
        }
        setPadding(px(8), px(1), px(8), px(1))
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = px(8) }
    }

    private fun GradientDrawable.color(c: Int) = setColor(ColorStateList.valueOf(c))

    private fun groupHeader(s: String) = text(s, M.attr.textAppearanceLabelLarge, muted = true).pad(top = 20, bottom = 8)

    private fun text(s: String, appearance: Int, muted: Boolean = false) = TextView(this).apply {
        text = s
        setTextAppearance(themeAttr(appearance))
        if (muted) setTextColor(themeColor(M.attr.colorOnSurfaceVariant))
        setTextIsSelectable(true)
    }

    private fun <T : View> T.pad(left: Int = 0, top: Int = 0, bottom: Int = 0) = apply { setPadding(px(left), px(top), 0, px(bottom)) }

    private fun px(v: Int) = (v * dp).toInt()

    private fun open(url: String) = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun themeAttr(attr: Int): Int {
        val v = android.util.TypedValue()
        theme.resolveAttribute(attr, v, true)
        return v.resourceId
    }

    private fun themeColor(attr: Int): Int {
        val v = android.util.TypedValue()
        theme.resolveAttribute(attr, v, true)
        return v.data
    }

    companion object {
        const val EXTRA_ID = "id"
        private val TIME = DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm")
        private val TIME_ONLY = Regex("""\d{1,2}:\d{2}""")

        /** 앱별 색 (배경, 글자). 웹 페이지와 같다. */
        private val APP_COLORS = mapOf(
            "카카오톡" to (Color.parseColor("#FEE500") to Color.parseColor("#3A1D1D")),
            "메시지" to (Color.parseColor("#2FB36B") to Color.WHITE),
            "Teams" to (Color.parseColor("#5B5FC7") to Color.WHITE),
            "딩톡" to (Color.parseColor("#1E89FF") to Color.WHITE),
            "삼성 월렛" to (Color.parseColor("#1C2B4A") to Color.WHITE),
        )
    }
}
