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

    private fun save(kind: String, hhmm: String) = store.save(
        """{"id":"2026-11-22/${hhmm.replace(":", "")}00-$kind","kind":"$kind","title":"t","created":"2026-11-22T$hhmm:00+09:00","sections":[]}""",
    ).id

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
}
