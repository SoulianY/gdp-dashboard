package com.souliany.notilog

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.google.android.material.R as M
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** 하단 탭: 브리핑(목록) · 설정(알림 수집·숨기기·업로드). */
class MainActivity : AppCompatActivity() {
    private lateinit var settings: Settings
    private lateinit var store: BriefingStore
    private lateinit var nav: BottomNavigationView
    private var filter = ALL
    private var day: LocalDate? = null  // 날짜 선택기로 고른 날 (없으면 최근 전체)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        settings = Settings(this)
        store = BriefingStore(this)
        BriefingWorker.schedule(this)
        // 새 브리핑 알림을 띄우려면 알림 권한이 필요하다(Android 13+).
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        nav = findViewById(R.id.nav)
        nav.setOnItemSelectedListener { showTab(it.itemId); true }
        setUpSettings()
        showTab(R.id.tab_briefings)
    }

    override fun onResume() {
        super.onResume()
        renderBriefings()
        renderSettings()
    }

    override fun onPause() {
        super.onPause()
        settings.blockWords = findViewById<EditText>(R.id.words).text.toString()
        settings.myNames = findViewById<EditText>(R.id.names).text.toString()
    }

    private fun showTab(id: Int) {
        findViewById<View>(R.id.briefings_pane).visibility = if (id == R.id.tab_briefings) View.VISIBLE else View.GONE
        findViewById<View>(R.id.settings_pane).visibility = if (id == R.id.tab_settings) View.VISIBLE else View.GONE
        supportActionBar?.title = if (id == R.id.tab_briefings) "브리핑" else "설정"
        invalidateOptionsMenu()
    }

    // ---- 브리핑 탭 ----

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (nav.selectedItemId == R.id.tab_briefings) menuInflater.inflate(R.menu.briefings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.pick_date -> {
            pickDate()
            true
        }
        R.id.refresh -> {
            WorkManager.getInstance(this).getWorkInfoByIdLiveData(BriefingWorker.refresh(this)).observe(this) {
                if (it?.state?.isFinished == true) renderBriefings()
            }
            true
        }
        R.id.read_all -> {
            store.markRead(store.all().map { it.id })
            renderBriefings()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    /** 최근 30일 중 하루를 골라 그날 브리핑만 본다. */
    private fun pickDate() {
        val today = LocalDate.now()
        val utcMillis = { d: LocalDate -> d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        MaterialDatePicker.Builder.datePicker()
            .setTitleText("브리핑 날짜")
            .setSelection(utcMillis(day ?: today))
            .setCalendarConstraints(
                CalendarConstraints.Builder()
                    .setStart(utcMillis(today.minusDays(BriefingWorker.KEEP_DAYS)))
                    .setEnd(utcMillis(today))
                    .setValidator(DateValidatorPointBackward.now())
                    .build(),
            )
            .build()
            .apply {
                addOnPositiveButtonClickListener {
                    day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    renderBriefings()
                }
            }
            .show(supportFragmentManager, "date")
    }

    private fun renderBriefings() {
        val all = store.all().filter { day == null || localDate(it.created) == day }
        val newOf = { kind: String? -> all.count { (kind == null || it.kind == kind) && store.isNew(it.id) } }

        // 필터 칩: 전체 + 종류별, NEW 개수 표시
        val chips = findViewById<ChipGroup>(R.id.filters)
        chips.setOnCheckedStateChangeListener(null)
        chips.removeAllViews()
        day?.let { d ->
            chips.addView(Chip(this, null, M.attr.chipStyle).apply {
                text = d.format(DateTimeFormatter.ofPattern("M월 d일 (E)"))
                isCloseIconVisible = true
                setOnCloseIconClickListener { day = null; renderBriefings() }
                setOnClickListener { pickDate() }
            })
        }
        for ((key, label) in listOf(ALL to "전체") + Briefing.KINDS.toList()) {
            val n = newOf(key.takeIf { it != ALL })
            chips.addView(Chip(this, null, M.attr.chipStyle).apply {
                id = View.generateViewId()
                tag = key
                text = if (n > 0) "$label  NEW $n" else label
                isCheckable = true
                isChecked = key == filter
            })
        }
        chips.setOnCheckedStateChangeListener { group, ids ->
            filter = group.findViewById<Chip>(ids.first()).tag as? String ?: return@setOnCheckedStateChangeListener
            renderBriefings()
        }

        // 하단 탭 배지
        val total = newOf(null)
        nav.getOrCreateBadge(R.id.tab_briefings).apply { isVisible = total > 0; number = total }

        val list = findViewById<LinearLayout>(R.id.briefing_list)
        list.removeAllViews()
        if (all.isEmpty()) {
            list.addView(note(if (day != null) "이 날짜의 브리핑이 없습니다." else "브리핑을 받아 오는 중입니다. 오른쪽 위 새로고침을 눌러 보세요."))
            return
        }
        if (filter == ALL) {
            // 종류별 영역. 날짜를 고르지 않았으면 최근 3건씩, 영역 제목의 '모두 보기'로 그 종류만 보기
            val limit = if (day == null) PREVIEW else Int.MAX_VALUE
            for ((kind, label) in Briefing.KINDS) {
                val items = all.filter { it.kind == kind }
                if (items.isEmpty()) continue
                list.addView(header(label, newOf(kind), items.size > limit) { select(kind) })
                items.take(limit).forEach { list.addView(card(it)) }
            }
        } else {
            val items = all.filter { it.kind == filter }
            if (items.isEmpty()) list.addView(note(if (day != null) "이 날짜의 브리핑이 없습니다." else "최근 30일 동안 받은 브리핑이 없습니다."))
            items.forEach { list.addView(card(it)) }
        }
    }

    private fun select(kind: String) {
        filter = kind
        renderBriefings()
    }

    private fun header(label: String, newCount: Int, more: Boolean, onMore: () -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, dp(8))
            addView(TextView(context).apply {
                text = if (newCount > 0) "$label  ·  NEW $newCount" else label
                setTextAppearance(attr(M.attr.textAppearanceTitleMedium))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            if (more) addView(TextView(context).apply {
                text = "모두 보기 ›"
                setTextAppearance(attr(M.attr.textAppearanceLabelLarge))
                setOnClickListener { onMore() }
            })
        }

    private fun card(b: Briefing): View = LayoutInflater.from(this).inflate(R.layout.briefing_item, findViewById(R.id.briefing_list), false).apply {
        findViewById<View>(R.id.new_badge).visibility = if (store.isNew(b.id)) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.title).text = b.title
        findViewById<TextView>(R.id.time).text = shortTime(b.created)
        findViewById<TextView>(R.id.summary).text = b.summary
        setOnClickListener {
            startActivity(Intent(this@MainActivity, BriefingActivity::class.java).putExtra(BriefingActivity.EXTRA_ID, b.id))
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
    }

    private fun localDate(iso: String) = OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()

    /** 오늘이면 "13:28", 아니면 "11/6 22:57" */
    private fun shortTime(iso: String): String {
        val t = OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault())
        return t.format(DateTimeFormatter.ofPattern(if (t.toLocalDate() == LocalDate.now()) "HH:mm" else "M/d HH:mm"))
    }

    private fun note(s: String) = TextView(this).apply {
        text = s
        setPadding(0, dp(24), 0, 0)
    }

    // ---- 설정 탭 ----

    private fun setUpSettings() {
        val repo = findViewById<EditText>(R.id.repo).apply { setText(settings.repo) }
        val token = findViewById<EditText>(R.id.token).apply { setText(settings.token) }
        findViewById<EditText>(R.id.words).setText(settings.blockWords)
        findViewById<EditText>(R.id.names).setText(settings.myNames)
        findViewById<Button>(R.id.grant).setOnClickListener {
            startActivity(Intent(ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.save).setOnClickListener {
            settings.repo = repo.text.toString()
            settings.token = token.text.toString()
            UploadWorker.schedule(this)
            UploadWorker.runNow(this)
            renderSettings()
        }
    }

    private fun renderSettings() {
        val granted = packageName in NotificationManagerCompat.getEnabledListenerPackages(this)
        findViewById<TextView>(R.id.status).text = buildString {
            appendLine("알림 접근 권한: ${if (granted) "허용됨" else "필요함"}")
            appendLine("업로드 설정: ${if (settings.configured) settings.repo else "필요함"}")
            appendLine("올리기 대기: ${Outbox(this@MainActivity).count()}건")
            append("마지막 업로드: ${settings.lastResult.ifEmpty { "없음" }}")
        }
        // 알림을 보낸 앱마다 '알림 띄우기' 스위치. 끈 앱은 기록만 하고 알림창에서 숨긴다.
        switches(R.id.apps, settings.seen(), "알림이 오면 여기에 앱이 나타납니다.", { it.pkg !in settings.hidden }) { app, on ->
            settings.setShown(app.pkg, on)
        }
        // 카톡 단톡방마다 '1분 제한' 스위치. 켠 방은 1분에 한 번만 알림이 뜬다(내 이름 언급은 예외).
        switches(R.id.rooms, settings.rooms(), "카톡 단톡방 알림이 오면 여기에 방이 나타납니다.", { it.pkg in settings.throttledRooms }) { room, on ->
            settings.setThrottled(room.pkg, on)
        }
    }

    private fun switches(
        listId: Int,
        rows: List<SeenApp>,
        empty: String,
        checked: (SeenApp) -> Boolean,
        onChange: (SeenApp, Boolean) -> Unit,
    ) {
        val list = findViewById<LinearLayout>(listId)
        list.removeAllViews()
        if (rows.isEmpty()) list.addView(TextView(this).apply { text = empty })
        for (row in rows) {
            list.addView(MaterialSwitch(this).apply {
                text = "${row.label}  ·  ${DateUtils.getRelativeTimeSpanString(row.last)}"
                isChecked = checked(row)
                setOnCheckedChangeListener { _, on -> onChange(row, on) }
            })
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun attr(a: Int): Int {
        val v = android.util.TypedValue()
        theme.resolveAttribute(a, v, true)
        return v.resourceId
    }

    private companion object {
        const val ALL = "all"
        const val PREVIEW = 3
    }
}
