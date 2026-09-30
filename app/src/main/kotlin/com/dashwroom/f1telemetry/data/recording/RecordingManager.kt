package com.dashwroom.f1telemetry.data.recording

import android.content.Context
import com.dashwroom.f1telemetry.core.TelemetryRepository
import com.dashwroom.f1telemetry.di.ApplicationScope
import com.dashwroom.f1telemetry.replay.record.PacketRecorder
import com.dashwroom.f1telemetry.replay.record.RecordingFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class RecordingFile(val path: String, val name: String, val sizeBytes: Long, val modifiedMillis: Long)

data class RecordingState(
    val active: Boolean = false,
    val fileName: String? = null,
    val packets: Long = 0,
    val bytes: Long = 0,
    val dropped: Long = 0,
)

/** Captures raw datagrams to `.bin` files in app storage (see [RecordingFormat]). */
@Singleton
class RecordingManager @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: TelemetryRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    val directory = File(context.filesDir, "recordings")
    private val recorder = PacketRecorder()
    private var ticker: Job? = null

    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _recordings = MutableStateFlow<List<RecordingFile>>(emptyList())
    val recordings: StateFlow<List<RecordingFile>> = _recordings.asStateFlow()

    init {
        scope.launch { refresh() }
    }

    fun start() {
        scope.launch(Dispatchers.IO) {
            if (recorder.isRecording) return@launch
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = File(directory, "session-$stamp.${RecordingFormat.FILE_EXTENSION}")
            recorder.start(file)
            repository.setTap(recorder)
            ticker = scope.launch {
                while (isActive) {
                    _state.value = RecordingState(true, file.name, recorder.packetsRecorded, recorder.bytesWritten, recorder.packetsDropped)
                    delay(500)
                }
            }
        }
    }

    fun stop() {
        scope.launch(Dispatchers.IO) {
            repository.setTap(null)
            recorder.stop()
            ticker?.cancel()
            _state.value = RecordingState()
            refresh()
        }
    }

    fun delete(path: String) {
        scope.launch(Dispatchers.IO) {
            File(path).takeIf { it.parentFile == directory }?.delete()
            refresh()
        }
    }

    /** The newest recording, used when the replay source has no explicit file. */
    suspend fun latest(): File? = withContext(Dispatchers.IO) { listFiles().firstOrNull() }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _recordings.value = listFiles().map { RecordingFile(it.absolutePath, it.name, it.length(), it.lastModified()) }
    }

    private fun listFiles(): List<File> =
        directory.listFiles { f -> f.isFile && f.extension == RecordingFormat.FILE_EXTENSION }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
}
