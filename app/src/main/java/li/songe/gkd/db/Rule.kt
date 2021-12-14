package li.songe.gkd.db

import androidx.room.*
import kotlin.random.Random


@Entity(tableName = "rule")
data class Rule(
    @PrimaryKey() @ColumnInfo(name = "id") val id: Long = Random.nextLong(
        -9007199254740991L,
        9007199254740991L
    ),
    @ColumnInfo(name = "package_name") var packageName: String,
    @ColumnInfo(name = "class_name") var className: String,
    @ColumnInfo(name = "selector") var selector: String,
    @ColumnInfo(name = "description") var description: String = "",
    @ColumnInfo(name = "rule_group_id") var ruleGroupUid: Long,
    @ColumnInfo(name = "ctime") var ctime: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "mtime") var mtime: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "disable") var disable: Boolean = false,
    /**
     * 规则序列号, 先匹配 seq 最小的规则, 如果序列号相等, 则执行顺序是未知的
     */
    @ColumnInfo(name = "seq") var seq: Int = 0,
)

@Dao
interface RuleDao {
    @Query("SELECT * FROM rule")
    suspend fun query(): MutableList<Rule>

    @Update
    suspend fun update(vararg args: Rule)

    @Delete
    suspend fun delete(vararg args: Rule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg args: Rule)
}
