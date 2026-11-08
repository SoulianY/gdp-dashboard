package com.souliany.notilog

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

// 화면 확인용 캡처: build/shots 폴더의 PNG (에뮬레이터 없이 화면 배치를 보기 위함)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "ko-w400dp-h860dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun seed() {
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
        val store = BriefingStore(ctx)
        val saved = listOf("noti", "daily", "news", "politics").map {
            store.save(javaClass.getResource("/samples/$it.json")!!.readText())
        }
        store.initialized = true
        store.markRead(listOf(saved[3].id))
    }

    @Test
    fun screens() {
        ActivityScenario.launch(MainActivity::class.java).onActivity {
            shot(it.window.decorView, "1-briefings")
            it.findViewById<BottomNavigationView>(R.id.nav).selectedItemId = R.id.tab_settings
        }.onActivity { shot(it.window.decorView, "4-settings") }
        detail("noti", "2-detail").onActivity {
            // 제외 목록(접힌 섹션)은 제목 줄을 누르면 펼쳐진다
            val root = it.window.decorView
            assertTrue(root.viewsWithText("쿠폰 도착").isEmpty()) // 숨은 뷰는 찾히지 않는다
            (root.viewsWithText("펼치기").single().parent as View).performClick()
            assertTrue(root.viewsWithText("쿠폰 도착").single().isShown)
            assertTrue(root.viewsWithText("앱 열기").let { b -> b.size == 2 && b.all(View::isShown) }) // 알림을 보낸 앱으로 이동
        }
        detail("news", "3-news")
    }

    @Test
    @Config(qualifiers = "+night")
    fun night() {
        ActivityScenario.launch(MainActivity::class.java).onActivity { shot(it.window.decorView, "5-night-briefings") }
        detail("noti", "6-night-detail")
    }

    private fun detail(kind: String, name: String): ActivityScenario<BriefingActivity> {
        val id = BriefingStore(ctx).all().first { it.kind == kind }.id
        return ActivityScenario.launch<BriefingActivity>(
            Intent(ctx, BriefingActivity::class.java).putExtra(BriefingActivity.EXTRA_ID, id),
        ).onActivity { shot(it.window.decorView, name) }
    }

    private fun View.viewsWithText(s: String): List<View> =
        ArrayList<View>().also { findViewsWithText(it, s, View.FIND_VIEWS_WITH_TEXT) }

    private fun shot(v: View, name: String) {
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File("build/shots").mkdirs()
        File("build/shots/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
