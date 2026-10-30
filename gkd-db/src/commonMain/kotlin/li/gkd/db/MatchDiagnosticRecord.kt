package li.gkd.db

import androidx.room3.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "match_diagnostic")
@ColumnTypeConverters(MatchDiagnosticAppIdsConverter::class)
data class MatchDiagnosticRecord(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "stopped_at") val stoppedAt: Long,
    @ColumnInfo(name = "duration_millis") val durationMillis: Long,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "app_ids") val appIds: List<String>,
)

class MatchDiagnosticAppIdsConverter {
    @ColumnTypeConverter fun encode(value: List<String>): String = Json.encodeToString(value)

    @ColumnTypeConverter fun decode(value: String): List<String> = Json.decodeFromString(value)
}

@Dao
interface MatchDiagnosticRecordDao {
    @Query("SELECT * FROM match_diagnostic ORDER BY id DESC")
    fun observeAll(): Flow<List<MatchDiagnosticRecord>>

    @Upsert suspend fun upsert(record: MatchDiagnosticRecord)

    @Query("DELETE FROM match_diagnostic WHERE id = :id") suspend fun delete(id: Long)
}
