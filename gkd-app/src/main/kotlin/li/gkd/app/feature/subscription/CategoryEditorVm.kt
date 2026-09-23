package li.gkd.app.feature.subscription

import kotlinx.coroutines.flow.flowOf
import li.gkd.app.ui.share.EditorSaveSession
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.subscription.SubscriptionRepository
import li.gkd.app.ui.share.BaseViewModel

class CategoryEditorVm(private val route: CategoryEditorRoute) : BaseViewModel() {
    val uiState = requiredSubscription(route.subsId).buildUiState(initialValue = { it }) { flowOf(it) }
    private val saveSession = EditorSaveSession<Boolean>()

    suspend fun save(subscription: RawSubscription, name: String, description: String): Boolean =
        saveSession.save {
            SubscriptionRepository.saveCategory(subscription, route.categoryKey, name, description)
        }
}
