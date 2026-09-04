package com.aptprice.tracker

import android.app.Application
import com.aptprice.tracker.core.crash.CrashReporter
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AptPriceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 처리되지 못한 예외를 파일에 남겨, 다음 실행 때 화면에서 확인할 수 있게 한다.
        // 폰만으로 쓰는 상황에서는 logcat 을 볼 수 없기 때문이다.
        CrashReporter.install(this)
    }
}
