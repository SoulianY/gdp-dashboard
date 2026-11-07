package com.souliany.notilog

import android.content.Context
import java.io.File

/**
 * 올리기 전 알림을 쌓아 두는 로컬 보관함.
 * 알림은 pending.jsonl에 한 줄씩 붙이고, 올릴 때 seal()로 batches/에 묶음 파일로 옮긴다.
 * 묶음 파일은 업로드에 성공해야 지워지므로, 네트워크가 끊겨도 잃지 않는다.
 */
class Outbox(context: Context) {
    private val dir = context.filesDir
    private val pending = File(dir, "pending.jsonl")
    private val batchDir = File(dir, "batches").apply { mkdirs() }

    fun append(line: String) = synchronized(LOCK) {
        pending.appendText(line + "\n")
    }

    /** 쌓인 알림을 새 묶음으로 봉인하고, 올릴 묶음 전체를 오래된 순으로 돌려준다. */
    fun seal(): List<File> = synchronized(LOCK) {
        if (pending.length() > 0) pending.renameTo(File(batchDir, "${System.currentTimeMillis()}.jsonl"))
        batchDir.listFiles().orEmpty().sortedBy { it.name }
    }

    fun count(): Int = synchronized(LOCK) {
        (listOf(pending) + batchDir.listFiles().orEmpty())
            .filter { it.exists() }
            .sumOf { f -> f.useLines { it.count() } }
    }

    private companion object {
        val LOCK = Any()
    }
}
