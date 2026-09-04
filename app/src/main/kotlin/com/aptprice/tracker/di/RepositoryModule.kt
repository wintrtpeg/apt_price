package com.aptprice.tracker.di

import com.aptprice.tracker.data.local.dao.RentDao
import com.aptprice.tracker.data.local.dao.SyncStateDao
import com.aptprice.tracker.data.local.dao.TradeDao
import com.aptprice.tracker.data.remote.api.MolitApiService
import com.aptprice.tracker.data.remote.api.ServiceKeyProvider
import com.aptprice.tracker.data.repository.TradeRepositoryImpl
import com.aptprice.tracker.domain.repository.TradeRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideTradeRepository(
        api: MolitApiService,
        serviceKey: ServiceKeyProvider,
        tradeDao: TradeDao,
        rentDao: RentDao,
        syncStateDao: SyncStateDao,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        clock: Clock,
    ): TradeRepository = TradeRepositoryImpl(
        api = api,
        serviceKey = serviceKey,
        tradeDao = tradeDao,
        rentDao = rentDao,
        syncStateDao = syncStateDao,
        ioDispatcher = ioDispatcher,
        clock = clock,
    )
}
