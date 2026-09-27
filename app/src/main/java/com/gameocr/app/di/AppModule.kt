package com.gameocr.app.di

import com.gameocr.app.BuildConfig
import com.gameocr.app.data.AndroidKeystoreSettingsSecretCipher
import com.gameocr.app.data.SettingsSecretCipher
import com.gameocr.app.game.session.GameSessionStore
import com.gameocr.app.game.session.SettingsGameSessionStore
import com.gameocr.app.network.NetworkPerformanceEventListener
import com.gameocr.app.network.DebugHttpWireLoggingInterceptor
import com.gameocr.app.util.RuntimePerformanceDiagnostics
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(
        privateCleartextInterceptor: PrivateCleartextInterceptor,
        performanceDiagnostics: RuntimePerformanceDiagnostics,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .eventListenerFactory(
            NetworkPerformanceEventListener.Factory(performanceDiagnostics)
        )
        // 明文 HTTP 仅允许私有/回环地址 + 用户显式白名单 host。详见拦截器注释。
        .addInterceptor(privateCleartextInterceptor)
        .apply {
            if (BuildConfig.DEBUG) {
                // Complete request/response logs for Debug builds only. The interceptor preserves
                // streaming delivery and redacts credentials before writing to Logcat.
                addInterceptor(DebugHttpWireLoggingInterceptor())
            }
        }
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EngineBindings {

    @Binds
    @Singleton
    abstract fun bindGameSessionStore(impl: SettingsGameSessionStore): GameSessionStore

    @Binds
    @Singleton
    abstract fun bindSettingsSecretCipher(
        impl: AndroidKeystoreSettingsSecretCipher
    ): SettingsSecretCipher
}
