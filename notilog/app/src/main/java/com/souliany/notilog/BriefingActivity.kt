package com.souliany.notilog

import android.content.Intent
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
import com.google.android.material.button.MaterialButton
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * 브리핑 한 건 보기. 열면 읽음으로 표시한다.
 * 머리(종류·제목·시각·요약) 아래에 메시지 영역(채팅방별 면, 왼쪽에 앱 색 띠)과 알림·요약 영역(면)을 두고, 제외 목록은 접어 둔다.
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
            title = ""
            setDisplayHomeAsUpEnabled(true)
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), 0, px(16), px(32))
        }
        body.addView(text(Briefing.KINDS[b.kind] ?: b.kind, R.style.Text_Title, selectable = false)
            .apply { setTextColor(Palette.kind(context, b.kind)) }.pad(4, 4))
        // 제목 파일의 title은 '종류 + 시각'이라, 시각을 크게(목록의 시각 열과 같은 모양) 쓰고 날짜를 붙인다.
        val created = OffsetDateTime.parse(b.created)
        body.addView(text(created.format(CLOCK), R.style.Text_Clock_Large).pad(4, 2))
        body.addView(text(created.format(DATE), R.style.Text_Small).pad(4, 2))
        if (b.summary.isNotEmpty()) body.addView(text(b.summary, R.style.Text_BodyLarge).pad(4, 12))

        // 뉴스·정치의 첫 섹션 '요약'은 위의 summary와 같은 글이라 뺀다.
        val sections = b.sections.filterNot { it.items.size == 1 && it.items[0].text == b.summary }
        val chats = sections.filter { it.chat }
        val rest = sections.filter { !it.chat }
        // 영역 제목은 메시지와 알림·요약이 함께 있을 때만
        val both = chats.isNotEmpty() && rest.isNotEmpty()
        if (both) body.addView(groupHeader("메시지"))
        chats.forEach { body.addView(section(it, article = false)) }
        body.addView(groupHeader(if (both) "알림·요약" else ""))
        rest.forEach { body.addView(section(it, article = b.kind in ARTICLES)) }
        findViewById<ScrollView>(R.id.scroll).apply {
            addView(body)
            // 하단 내비게이션 바에 마지막 버튼이 가리지 않게
            ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
                v.setPadding(0, 0, 0, insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
                insets
            }
        }
    }

    /** 섹션 하나 = 흰 면 하나. 메시지는 왼쪽에 앱 색 띠, 제목 앞에 앱 배지, 줄마다 오른쪽에 시각. */
    private fun section(s: Briefing.Section, article: Boolean): View {
        val color = Palette.APP[s.app]
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(14), px(16), px(14))
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (s.app.isNotEmpty()) head.addView(badge(s.app, color))
        // 제목 줄은 선택 불가: 선택 가능한 글자는 탭을 가로채 접힌 섹션이 펼쳐지지 않는다.
        head.addView(text(s.title, R.style.Text_Title, selectable = false), LinearLayout.LayoutParams(0, -2, 1f))
        if (s.chat) head.addView(text("${s.items.size}개", R.style.Text_Small, selectable = false))
        inner.addView(head)

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = if (s.fold) View.GONE else View.VISIBLE }
        for (item in s.items) list.addView(row(item, article))
        inner.addView(list)
        if (s.fold) {
            val toggle = text(FOLDED, R.style.Text_Small, selectable = false).apply { setTextColor(getColor(R.color.primary)) }
            head.addView(toggle)
            head.setOnClickListener {
                val open = list.visibility == View.GONE
                list.visibility = if (open) View.VISIBLE else View.GONE
                toggle.text = if (open) UNFOLDED else FOLDED
            }
        }

        // 흰 면 하나. 메시지는 안쪽 왼쪽에 둥근 앱 색 띠
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.sheet)
            if (s.chat && color != null) addView(View(context).apply {
                background = GradientDrawable().apply { cornerRadius = px(2).toFloat(); setColor(color.first) }
            }, LinearLayout.LayoutParams(px(4), -1).apply { setMargins(px(10), px(14), 0, px(14)) })
            addView(inner, LinearLayout.LayoutParams(0, -2, 1f))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(10) }
        }
    }

    /** 한 줄: (사진) + 본문 + (시각이면 오른쪽, 아니면 아래 회색 글씨) + 링크·앱 열기.
     *  article(뉴스·정치)이면 본문이 기사 제목, 아래가 기사 내용이라 둘 다 크게. */
    private fun row(item: Briefing.Item, article: Boolean): View {
        val isTime = TIME_ONLY.matches(item.sub)
        val launch = item.pkg.takeIf { it.isNotEmpty() }?.let(packageManager::getLaunchIntentForPackage)
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, px(10), 0, 0)
            addView(text(item.text, if (article) R.style.Text_Article else R.style.Text_Body), LinearLayout.LayoutParams(0, -2, 1f))
            if (isTime) addView(text(item.sub, R.style.Text_Small).pad(left = 12).apply { fontFeatureSettings = "tnum" })
        }
        if (isTime && item.link.isEmpty() && item.image.isEmpty() && launch == null) return line
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            if (item.image.isNotEmpty()) addView(photo(item.image), LinearLayout.LayoutParams(-1, px(184)).apply { topMargin = px(12) })
            addView(line)
            if (!isTime && item.sub.isNotEmpty()) addView(text(item.sub, if (article) R.style.Text_Body else R.style.Text_Small).pad(top = 4))
            if (item.link.isNotEmpty()) addView(action(if ("calendar.google.com" in item.link) "캘린더에 등록" else "원문 보기") { open(item.link) })
            if (launch != null) addView(action("앱 열기") { startActivity(launch) })
        }
    }

    /** 줄 아래 왼쪽 정렬 글자 버튼 */
    private fun action(label: String, onClick: () -> Unit) = MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
        text = label
        setTextAppearance(R.style.Text_Label)
        setTextColor(getColor(R.color.primary))
        minHeight = 0
        minimumHeight = 0
        setPadding(0, px(6), px(12), px(2))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-2, -2)
    }

    /** 기사 사진. 못 받아 오면 자리를 비운다(웹 페이지와 같음). */
    private fun photo(url: String) = ImageView(this).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        clipToOutline = true
        background = GradientDrawable().apply { cornerRadius = px(10).toFloat(); setColor(getColor(R.color.fog)) }
        load(url) { listener(onError = { _, _ -> visibility = View.GONE }) }
    }

    private fun badge(app: String, color: Pair<Int, Int>?) = TextView(this).apply {
        text = app
        setTextAppearance(R.style.Text_LabelMedium)
        setTextColor(color?.second ?: getColor(R.color.ink))
        background = GradientDrawable().apply {
            cornerRadius = px(10).toFloat()
            setColor(color?.first ?: getColor(R.color.rule))
        }
        setPadding(px(8), px(2), px(8), px(2))
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = px(8) }
    }

    private fun groupHeader(s: String) = text(s, R.style.Text_Small, selectable = false).pad(4, 22, 8)

    private fun text(s: String, style: Int, selectable: Boolean = true) = TextView(this).apply {
        text = s
        setTextAppearance(style)
        setTextIsSelectable(selectable)
    }

    private fun <T : View> T.pad(left: Int = 0, top: Int = 0, bottom: Int = 0) = apply { setPadding(px(left), px(top), 0, px(bottom)) }

    private fun px(v: Int) = (v * dp).toInt()

    private fun open(url: String) = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_ID = "id"
        private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
        private val DATE = DateTimeFormatter.ofPattern("M월 d일 EEEE")
        private val TIME_ONLY = Regex("""\d{1,2}:\d{2}""")
        private val ARTICLES = setOf("news", "politics")
        private const val FOLDED = "펼치기 ▾"
        private const val UNFOLDED = "접기 ▴"
    }
}
