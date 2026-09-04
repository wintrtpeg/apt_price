package com.aptprice.tracker.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aptprice.tracker.data.remote.api.ServiceKeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * 인증키를 DataStore 에 보관한다.
 *
 * 키는 사용자 본인의 것이고 기기 밖으로 나가지 않는다. 앱 전용 저장소에 있어
 * 다른 앱이 읽을 수 없다.
 */
class DataStoreServiceKeyStore(
    private val dataStore: DataStore<Preferences>,
) : ServiceKeyStore {

    override val keyFlow: Flow<String> = dataStore.data
        // 저장소를 읽지 못하는 상황에서도 앱이 멈추지 않게 한다. 키가 없는 것으로 본다.
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it[KEY] ?: "" }

    override suspend fun save(key: String) {
        dataStore.edit { it[KEY] = key }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("molit_service_key")
    }
}
