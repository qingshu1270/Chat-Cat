package com.chatcat

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.chatcat.data.ChatRepository
import com.chatcat.data.archive.ChatArchive
import com.chatcat.data.archive.ImportCache
import com.chatcat.data.db.AppDatabase
import com.chatcat.data.prefs.SettingsStore
import com.chatcat.data.remote.FilesApi
import com.chatcat.data.remote.BalanceApi
import com.chatcat.data.holiday.HolidayRepository
import com.chatcat.data.remote.MessagesApi
import com.chatcat.domain.context.ContextAssembler
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class AppContainer(private val context: Context) {

    val settings = SettingsStore(context)
    val assembler = ContextAssembler()

    private val database: AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        AppDatabase.NAME,
    ).addMigrations(
        AppDatabase.MIGRATION_1_2,
        AppDatabase.MIGRATION_2_3,
        AppDatabase.MIGRATION_3_4,
    ).build()

    val repository = ChatRepository(database)

    /** 节假日数据：在线拉取 + 本地缓存（12 小时内不重复请求） */
    val holidays by lazy { HolidayRepository(httpClient, settings) }
    val archive = ChatArchive(database)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val messagesApi = MessagesApi(httpClient)
    val filesApi = FilesApi(httpClient)
    val balanceApi = BalanceApi(httpClient)
    val archiveDownloader = com.chatcat.data.remote.ArchiveDownloader(httpClient)

    fun settleDangling() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            repository.settleDangling()
        }
    }

    /**
     * 启动时清掉上次导入流程残留的临时包（进程被杀时没人删，单个包可能上百 MB）。
     */
    fun sweepImportCache() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { ImportCache.sweepAll(context.cacheDir) }
        }
    }
}

class ChatCatApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.settleDangling()
        container.sweepImportCache()
    }
}
