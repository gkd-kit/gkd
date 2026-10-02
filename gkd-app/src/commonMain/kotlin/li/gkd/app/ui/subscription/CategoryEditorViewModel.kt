package li.gkd.app.ui.subscription

import kotlinx.coroutines.flow.flowOf
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.ui.navigation.CategoryEditorRoute
import li.gkd.app.ui.state.BaseViewModel

class CategoryEditorViewModel(
    private val route: CategoryEditorRoute,
) : BaseViewModel() {
    val uiState = RequiredSubscription(
        route.subsId,
        scope
    ).buildUiState(initialValue = { it }) { flowOf(it) }
    private val saveSession = EditorSaveSession<Boolean>()

    suspend fun save(subscription: RawSubscription, name: String, description: String): Boolean =
        saveSession.save {
            SubscriptionRepository.saveCategory(subscription, route.categoryKey, name, description)
        }
}
