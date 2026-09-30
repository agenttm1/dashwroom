package com.dashwroom.f1telemetry.di

import android.content.Context
import android.os.Process
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.dashwroom.f1telemetry.core.TelemetryRepository
import com.dashwroom.f1telemetry.data.history.HistoryDao
import com.dashwroom.f1telemetry.data.history.HistoryDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    @IngestDispatcher
    fun ingestDispatcher(): CoroutineDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread({
            // Data that feeds the display: run just below the UI thread's own urgency.
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
            runnable.run()
        }, "telemetry-ingest").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    @Provides
    @Singleton
    fun telemetryRepository(
        @ApplicationScope scope: CoroutineScope,
        @IngestDispatcher ingest: CoroutineDispatcher,
    ): TelemetryRepository = TelemetryRepository(scope, ingest)

    @Provides
    @Singleton
    fun historyDatabase(@ApplicationContext context: Context): HistoryDatabase =
        Room.databaseBuilder(context, HistoryDatabase::class.java, "history.db").build()

    @Provides
    fun historyDao(db: HistoryDatabase): HistoryDao = db.dao()

    @Provides
    @Singleton
    fun settingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.settingsDataStore
}
