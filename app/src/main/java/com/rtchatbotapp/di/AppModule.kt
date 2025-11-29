package com.rtchatbotapp.di

import android.content.Context
import androidx.room.Room
import com.rtchatbotapp.BuildConfig
import com.rtchatbotapp.data.source.local.AppDatabase
import com.rtchatbotapp.data.source.local.ChatThreadDao
import com.rtchatbotapp.data.source.local.QueuedMessageDao
import com.rtchatbotapp.data.source.remote.SocketClient
import com.rtchatbotapp.util.NetworkObserver
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
        val url =  BuildConfig.WS_URL
        val apiKey = BuildConfig.WS_API_KEY
        return SocketClient(
            url = "$url?apiKey=$apiKey",
            okHttpClient = okHttpClient
        )
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME).build()

    @Provides
    fun provideQueuedDao(db: AppDatabase): QueuedMessageDao = db.queuedMessageDao()

    @Provides
    fun provideChatThreadDao(db: AppDatabase): ChatThreadDao = db.chatThreadDao()

    @Provides
    @Singleton
    fun provideNetworkObserver(@ApplicationContext context: Context): NetworkObserver =
        NetworkObserver(context)

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

}