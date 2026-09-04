package com.aptprice.tracker.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.aptprice.tracker.BuildConfig
import com.aptprice.tracker.data.local.AptPriceDatabase
import com.aptprice.tracker.data.local.DataStoreServiceKeyStore
import com.aptprice.tracker.data.local.dao.RentDao
import com.aptprice.tracker.data.local.dao.SyncStateDao
import com.aptprice.tracker.data.local.dao.TradeDao
import com.aptprice.tracker.data.remote.api.MolitApiService
import com.aptprice.tracker.data.remote.api.ServiceKeyProvider
import com.aptprice.tracker.data.remote.api.ServiceKeyStore
import com.aptprice.tracker.data.remote.throttle.ThrottleInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile(PREFERENCES_NAME)
    }

    @Provides
    @Singleton
    fun provideServiceKeyStore(dataStore: DataStore<Preferences>): ServiceKeyStore =
        DataStoreServiceKeyStore(dataStore)

    /**
     * 인증키는 앱에서 입력한 값을 먼저 쓰고, 없으면 빌드 시 주입된 값으로 넘어간다.
     * 덕분에 APK 에 키를 넣지 않고도 배포할 수 있다.
     */
    @Provides
    @Singleton
    fun provideServiceKeyProvider(store: ServiceKeyStore): ServiceKeyProvider =
        ServiceKeyProvider(store, BuildConfig.MOLIT_SERVICE_KEY)

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // 요청 간격 조절과 429 재시도. 속도 제한은 전송 계층에서 처리한다.
        .addInterceptor(ThrottleInterceptor())
        .apply {
            if (BuildConfig.DEBUG) {
                // 인증키가 로그에 남지 않도록 본문/헤더는 찍지 않는다.
                addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
                )
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(MolitApiService.BASE_URL)
        .client(client)
        // 응답을 문자열로 받아 MolitParser 가 직접 해석한다.
        .addConverterFactory(ScalarsConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideMolitApiService(retrofit: Retrofit): MolitApiService =
        retrofit.create(MolitApiService::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AptPriceDatabase =
        Room.databaseBuilder(context, AptPriceDatabase::class.java, AptPriceDatabase.NAME)
            .build()

    @Provides
    fun provideTradeDao(db: AptPriceDatabase): TradeDao = db.tradeDao()

    @Provides
    fun provideRentDao(db: AptPriceDatabase): RentDao = db.rentDao()

    @Provides
    fun provideSyncStateDao(db: AptPriceDatabase): SyncStateDao = db.syncStateDao()

    private const val PREFERENCES_NAME = "apt_price_settings"
}
