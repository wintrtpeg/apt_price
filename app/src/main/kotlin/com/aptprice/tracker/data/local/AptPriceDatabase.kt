package com.aptprice.tracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aptprice.tracker.data.local.dao.RentDao
import com.aptprice.tracker.data.local.dao.SyncStateDao
import com.aptprice.tracker.data.local.dao.TradeDao
import com.aptprice.tracker.data.local.entity.RentEntity
import com.aptprice.tracker.data.local.entity.SyncStateEntity
import com.aptprice.tracker.data.local.entity.TradeEntity

@Database(
    entities = [TradeEntity::class, RentEntity::class, SyncStateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AptPriceDatabase : RoomDatabase() {
    abstract fun tradeDao(): TradeDao
    abstract fun rentDao(): RentDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        const val NAME = "apt_price.db"
    }
}
