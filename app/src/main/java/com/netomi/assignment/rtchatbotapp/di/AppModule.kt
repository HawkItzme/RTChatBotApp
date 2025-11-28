package com.netomi.assignment.rtchatbotapp.di

import android.content.Context
import androidx.room.Room
import com.netomi.assignment.rtchatbotapp.data.source.local.AppDatabase
import com.netomi.assignment.rtchatbotapp.data.source.local.QueuedMessageDao
import com.netomi.assignment.rtchatbotapp.data.source.remote.SocketClient
import com.netomi.assignment.rtchatbotapp.util.NetworkObserver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private const val DATABASE_NAME = "rtchat_db"
private const val DEFAULT_WS_URL = "GENERATE_URL_ON_SERVER"
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideSocketClient(@ApplicationContext context: Context, okHttpClient: OkHttpClient): SocketClient {
        // You can persist/configure the WS URL. For assignment provide constant or fetch from prefs.
        val url = DEFAULT_WS_URL
        return SocketClient(url = url, okHttpClient = okHttpClient)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME).build()

    @Provides
    fun provideQueuedDao(db: AppDatabase): QueuedMessageDao = db.queuedMessageDao()

    @Provides
    @Singleton
    fun provideNetworkObserver(@ApplicationContext context: Context): NetworkObserver =
        NetworkObserver(context)

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

}