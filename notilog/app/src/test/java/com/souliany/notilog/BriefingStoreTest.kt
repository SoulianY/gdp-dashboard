package com.souliany.notilog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BriefingStoreTest {
    private val store = BriefingStore(ApplicationProvider.getApplicationContext<Context>())

    private fun save(kind: String, hhmm: String, items: String = "") = store.save(
        """{"id":"2026-11-22/${hhmm.replace(":", "")}00-$kind","kind":"$kind","title":"$hhmm","created":"2026-11-22T$hhmm:00+09:00","summary":"s$hhmm",
           "sections":[{"title":"경제","items":[$items]}]}""",
    ).id

    private fun item(text: String, link: String) = """{"text":"$text","link":"$link"}"""

    @Test
    fun readThroughFollowsNewestReadNotiOnly() {
        val n13 = save("noti", "13:00")
        val n15 = save("noti", "15:00")
        val news = save("news", "16:00")
        store.markRead(listOf(n15, news))
        assertEquals("2026-11-22T15:00:00+09:00", store.notiReadThrough)
        store.markRead(listOf(n13))  // 예전 회차를 나중에 읽어도 뒤로 가지 않는다
        assertEquals("2026-11-22T15:00:00+09:00", store.notiReadThrough)
        store.delete(n13)
        assertFalse(store.has(n13))
    }

    @Test
    fun unreadRoundsMergeIntoNewest() {
        val read = save("news", "08:00", item("읽은 기사", "a"))
        store.markRead(listOf(read))
        val n11 = save("news", "11:00", item("금리 동결", "b") + "," + item("유가", "c"))
        val n14 = save("news", "14:00", item("금리 동결 후속", "b"))
        val merged = store.mergeUnread(store.get(save("news", "17:00", item("환율", "d")))!!)

        assertEquals("2026-11-22/170000-news", merged.id)
        assertEquals("17:00 (못 본 3회 합침)", merged.title)
        // 최신 회차 먼저, 같은 링크(b)는 최신 것만
        assertEquals(listOf("환율", "금리 동결 후속", "유가"), merged.sections.single().items.map { it.text })
        assertEquals("08:00", store.get(read)!!.title)  // 읽은 회차는 그대로
        assertEquals(null, store.get(n11))
        assertEquals(null, store.get(n14))
        org.junit.Assert.assertTrue(store.has(n11))  // 다시 받지 않게 기억
        assertEquals(merged, store.get(merged.id))
    }
}
