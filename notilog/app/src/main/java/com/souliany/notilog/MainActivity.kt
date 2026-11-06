package com.souliany.notilog

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat

class MainActivity : Activity() {
    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = Settings(this)

        val repo = findViewById<EditText>(R.id.repo).apply { setText(settings.repo) }
        val token = findViewById<EditText>(R.id.token).apply { setText(settings.token) }

        findViewById<Switch>(R.id.hide).apply {
            isChecked = settings.hideOthers
            setOnCheckedChangeListener { _, on -> settings.hideOthers = on }
        }
        findViewById<Button>(R.id.exempt).setOnClickListener { pickExempt() }

        findViewById<Button>(R.id.grant).setOnClickListener {
            startActivity(Intent(ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.save).setOnClickListener {
            settings.repo = repo.text.toString()
            settings.token = token.text.toString()
            UploadWorker.schedule(this)
            UploadWorker.runNow(this)
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    /** 런처에 보이는 앱 중 알림을 그대로 띄울 앱을 고른다. */
    private fun pickExempt() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedWith(compareBy({ it.first !in settings.exempt }, { it.second }))
        val checked = BooleanArray(apps.size) { apps[it].first in settings.exempt }
        AlertDialog.Builder(this)
            .setTitle("알림을 그대로 띄울 앱")
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("저장") { _, _ ->
                settings.exempt = apps.filterIndexed { i, _ -> checked[i] }.map { it.first }.toSet()
                render()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun render() {
        findViewById<Button>(R.id.exempt).text = "예외 앱 선택 (${settings.exempt.size}개)"
        val granted = packageName in NotificationManagerCompat.getEnabledListenerPackages(this)
        findViewById<TextView>(R.id.status).text = buildString {
            appendLine("알림 접근 권한: ${if (granted) "허용됨" else "필요함"}")
            appendLine("업로드 설정: ${if (settings.configured) settings.repo else "필요함"}")
            appendLine("올리기 대기: ${Outbox(this@MainActivity).count()}건")
            append("마지막 업로드: ${settings.lastResult.ifEmpty { "없음" }}")
        }
    }
}
