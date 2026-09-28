package com.gameocr.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryConcurrencyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /** 单测不碰 AndroidKeyStore：加前缀即可满足「密文 != 明文」的约束。 */
    private class PrefixCipher : SettingsSecretCipher {
        override fun encrypt(plainText: String): String = "x:$plainText"

        override fun decrypt(cipherText: String): String = cipherText.removePrefix("x:")
    }

    /**
     * 主界面写 API Key 与悬浮球写位置 / 会话写历史是并发路径。
     * update 必须在 DataStore 事务里读改写，否则后写的一方会把先写的字段覆盖回旧快照。
     */
    @Test
    fun `concurrent updates keep every field`() = runBlocking {
        val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val store = PreferenceDataStoreFactory.create(scope = storeScope) {
            File(tempFolder.root, "settings.preferences_pb")
        }
        try {
            val repository = SettingsRepository(PrefixCipher(), store)

            val apiKeyWriter = launch(Dispatchers.Default) {
                repeat(200) { repository.update { it.copy(apiKey = "key-value") } }
            }
            val moduleWriter = launch(Dispatchers.Default) {
                repeat(200) { repository.update { it.copy(gameModuleId = "doudizhu") } }
            }
            apiKeyWriter.join()
            moduleWriter.join()

            val settings = repository.get()
            assertEquals("key-value", settings.apiKey)
            assertEquals("doudizhu", settings.gameModuleId)
        } finally {
            storeScope.cancel()
        }
    }
}
