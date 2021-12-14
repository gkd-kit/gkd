package li.songe.gkd.db

import androidx.room.*
import kotlin.random.Random


@Entity(tableName = "rule_group")
data class RuleGroup(
    @PrimaryKey() @ColumnInfo(name = "id") val id: Long = Random.nextLong(
        -9007199254740991L,
        9007199254740991L
    ),
    @ColumnInfo(name = "package_name") var packageName: String,
    @ColumnInfo(name = "class_name") var className: String,
    @ColumnInfo(name = "description") var description: String = "",
    @ColumnInfo(name = "ctime") var ctime: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "mtime") var mtime: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "disable") var disable: Boolean = false,
)

data class RuleGroupWithRuleList(
    @Embedded val ruleGroup: RuleGroup,
    @Relation(
        parentColumn = "id",
        entityColumn = "rule_group_id"
    )
    val ruleList: MutableList<Rule>
)

@Dao
interface RuleGroupDao {
    @Query("SELECT * FROM rule_group")
    suspend fun query(): MutableList<RuleGroup>

    @Update
    suspend fun update(vararg args: RuleGroup)

    @Delete
    suspend fun delete(vararg args: RuleGroup)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg args: RuleGroup)

    @Transaction
    @Query("SELECT * FROM rule_group")
    suspend fun queryRuleGroupWithRuleList(): MutableList<RuleGroupWithRuleList>

}