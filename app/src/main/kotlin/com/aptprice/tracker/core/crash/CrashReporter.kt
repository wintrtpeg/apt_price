package com.aptprice.tracker.core.crash

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 앱이 죽은 이유를 다음 실행 때 화면에 보여 준다.
 *
 * PC 없이 폰만으로 쓰는 상황에서는 logcat 을 볼 수 없어, 앱이 조용히 종료되면
 * 원인을 알 길이 없다. 처리되지 못한 예외를 파일에 적어 두고 다음 실행에서 띄운다.
 *
 * 이 클래스는 Hilt·Room·네트워크에 의존하지 않는다. 크래시를 기록하는 코드가
 * 다시 크래시를 내면 안 되기 때문이다. 모든 동작은 실패해도 조용히 넘어간다.
 */
object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_LENGTH = 20_000

    /** [Application.onCreate] 에서 한 번 호출한다. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(appContext, thread, error) }
            // 원래 처리기에 넘겨 평소처럼 종료되게 한다. 여기서 삼키면 앱이 좀비가 된다.
            previous?.uncaughtException(thread, error)
        }
    }

    /** 지난 실행에서 남은 기록. 없으면 null. */
    fun read(context: Context): String? = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) file.readText().takeIf { it.isNotBlank() } else null
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val text = buildString {
            appendLine("발생 시각 : ${timestamp()}")
            appendLine("스레드     : ${thread.name}")
            appendLine("기기       : ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("안드로이드 : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("앱 버전    : ${versionOf(context)}")
            appendLine()
            appendLine(stackTraceOf(error))
        }
        File(context.filesDir, FILE_NAME).writeText(text.take(MAX_LENGTH))
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date())

    private fun versionOf(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${info.longVersionCodeCompat()})"
    }.getOrElse { "알 수 없음" }

    private fun android.content.pm.PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else @Suppress("DEPRECATION") versionCode.toLong()

    /** 원인 예외(cause)까지 따라가며 전체 스택을 남긴다. */
    private fun stackTraceOf(error: Throwable): String {
        val writer = StringWriter()
        PrintWriter(writer).use { error.printStackTrace(it) }
        return writer.toString()
    }
}
