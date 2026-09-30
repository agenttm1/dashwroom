package com.dashwroom.f1telemetry.data.history

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One game session (identified by the game's session UID) that produced at least one lap. */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val uid: Long,
    @ColumnInfo(name = "started_at") val startedAtMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
    @ColumnInfo(name = "track_id") val trackId: Int,
    @ColumnInfo(name = "track_name") val trackName: String,
    @ColumnInfo(name = "session_type") val sessionType: Int,
    @ColumnInfo(name = "session_type_name") val sessionTypeName: String,
    @ColumnInfo(name = "packet_format") val packetFormat: Int,
)

/**
 * One completed player lap with its telemetry traces. [traces] is [LapTraceCodec]'s compressed
 * encoding, so a lap costs a few tens of kilobytes.
 */
@Entity(
    tableName = "laps",
    foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["uid"], childColumns = ["session_uid"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["session_uid", "lap_number"], unique = true)],
)
data class LapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_uid") val sessionUid: Long,
    @ColumnInfo(name = "lap_number") val lapNumber: Int,
    @ColumnInfo(name = "lap_time_ms") val lapTimeMs: Long,
    @ColumnInfo(name = "s1_ms") val s1Ms: Int,
    @ColumnInfo(name = "s2_ms") val s2Ms: Int,
    @ColumnInfo(name = "s3_ms") val s3Ms: Int,
    val valid: Boolean,
    @ColumnInfo(name = "tyre_visual") val tyreVisual: Int,
    @ColumnInfo(name = "track_length_m") val trackLengthM: Float,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val traces: ByteArray,
) {
    override fun equals(other: Any?) = other is LapEntity && other.sessionUid == sessionUid && other.lapNumber == lapNumber && other.id == id

    override fun hashCode() = (sessionUid * 31 + lapNumber).toInt()
}

/** Session list row: the session plus aggregates over its laps. */
data class SessionSummaryRow(
    val uid: Long,
    @ColumnInfo(name = "started_at") val startedAtMillis: Long,
    @ColumnInfo(name = "track_name") val trackName: String,
    @ColumnInfo(name = "session_type_name") val sessionTypeName: String,
    @ColumnInfo(name = "lap_count") val lapCount: Int,
    @ColumnInfo(name = "best_lap_ms") val bestLapMs: Long?,
)

/** Lap list row, without the (large) trace blob. */
data class LapSummaryRow(
    @ColumnInfo(name = "session_uid") val sessionUid: Long,
    @ColumnInfo(name = "lap_number") val lapNumber: Int,
    @ColumnInfo(name = "lap_time_ms") val lapTimeMs: Long,
    @ColumnInfo(name = "s1_ms") val s1Ms: Int,
    @ColumnInfo(name = "s2_ms") val s2Ms: Int,
    @ColumnInfo(name = "s3_ms") val s3Ms: Int,
    val valid: Boolean,
    @ColumnInfo(name = "tyre_visual") val tyreVisual: Int,
)

@Dao
interface HistoryDao {
    @Upsert
    suspend fun upsertSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE uid = :uid")
    suspend fun session(uid: Long): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLap(lap: LapEntity)

    @Transaction
    suspend fun saveLap(session: SessionEntity, lap: LapEntity) {
        val existing = session(session.uid)
        upsertSession(if (existing == null) session else session.copy(startedAtMillis = existing.startedAtMillis))
        insertLap(lap)
    }

    @Query(
        """
        SELECT s.uid, s.started_at, s.track_name, s.session_type_name,
               COUNT(l.id) AS lap_count,
               MIN(CASE WHEN l.valid THEN l.lap_time_ms END) AS best_lap_ms
        FROM sessions s LEFT JOIN laps l ON l.session_uid = s.uid
        GROUP BY s.uid ORDER BY s.updated_at DESC
        """,
    )
    fun sessions(): Flow<List<SessionSummaryRow>>

    @Query("SELECT session_uid, lap_number, lap_time_ms, s1_ms, s2_ms, s3_ms, valid, tyre_visual FROM laps WHERE session_uid = :uid ORDER BY lap_number")
    fun laps(uid: Long): Flow<List<LapSummaryRow>>

    @Query("SELECT * FROM laps WHERE session_uid = :uid AND lap_number = :lap")
    suspend fun lap(uid: Long, lap: Int): LapEntity?

    @Query("DELETE FROM sessions WHERE uid = :uid")
    suspend fun deleteSession(uid: Long)

    /** Keeps the [keep] most recently updated sessions. */
    @Query("DELETE FROM sessions WHERE uid NOT IN (SELECT uid FROM sessions ORDER BY updated_at DESC LIMIT :keep)")
    suspend fun trimSessions(keep: Int)
}

@Database(entities = [SessionEntity::class, LapEntity::class], version = 1, exportSchema = true)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun dao(): HistoryDao
}
