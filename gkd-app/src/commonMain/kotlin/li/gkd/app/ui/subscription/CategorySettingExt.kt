package li.gkd.app.ui.subscription

import androidx.compose.ui.graphics.vector.ImageVector
import li.gkd.app.rule.CategorySetting
import li.gkd.app.ui.component.GkIcons

val CategorySetting.icon: ImageVector
    get() = when (this) {
        CategorySetting.FollowSubscription -> GkIcons.StackedDocuments
        CategorySetting.Enabled -> GkIcons.CheckCircle
        CategorySetting.Disabled -> GkIcons.RemoveCircleOutline
        CategorySetting.GroupDefault -> GkIcons.FlashOn
    }
