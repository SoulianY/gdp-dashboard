package com.souliany.notilog

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.R as M
import com.google.android.material.button.MaterialButton
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** 브리핑 한 건 보기. 열면 읽음으로 표시한다. */
class BriefingActivity : AppCompatActivity() {

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

        val dp = resources.displayMetrics.density
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), (32 * dp).toInt())
        }
        fun text(s: String, appearance: Int, top: Float = 0f) = TextView(this).apply {
            text = s
            setTextAppearance(themeAttr(appearance))
            setPadding(0, (top * dp).toInt(), 0, 0)
            setTextIsSelectable(true)
        }

        body.addView(text(b.title, M.attr.textAppearanceHeadlineSmall))
        body.addView(text(OffsetDateTime.parse(b.created).format(TIME), M.attr.textAppearanceBodySmall, 4f))
        for (section in b.sections) {
            body.addView(text(section.title, M.attr.textAppearanceTitleMedium, 24f))
            for (item in section.items) {
                body.addView(text(item.text, M.attr.textAppearanceBodyLarge, 10f))
                if (item.sub.isNotEmpty()) body.addView(text(item.sub, M.attr.textAppearanceBodySmall, 2f))
                if (item.link.isNotEmpty()) {
                    body.addView(MaterialButton(this, null, M.attr.materialButtonOutlinedStyle).apply {
                        text = if ("calendar.google.com" in item.link) "캘린더에 등록" else "원문 보기"
                        setOnClickListener { open(item.link) }
                    })
                }
            }
        }
        if (b.url.isNotEmpty()) {
            body.addView(MaterialButton(this).apply {
                text = "웹 페이지로 보기"
                setOnClickListener { open(b.url) }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (28 * dp).toInt() })
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

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun open(url: String) = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun themeAttr(attr: Int): Int {
        val v = android.util.TypedValue()
        theme.resolveAttribute(attr, v, true)
        return v.resourceId
    }

    companion object {
        const val EXTRA_ID = "id"
        private val TIME = DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm")
    }
}
