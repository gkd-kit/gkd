package li.songe.gkd.ui

import androidx.compose.runtime.Composable
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import li.songe.gkd.db.DbSet

@RootNavGraph
@Destination
@Composable
fun RecordPage() {
    DbSet.triggerLogDao
}