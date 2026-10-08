package li.gkd.app.ui.subscription

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionJson.json
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.ui.navigation.CategoryEditorRoute
import li.gkd.app.ui.state.BaseViewModel

data class CategoryEditorDraft(
    val originalCategory: String?,
    val originalName: String,
    val originalDescription: String,
    val name: String = originalName,
    val description: String = originalDescription,
)

class CategoryEditorViewModel(
    private val route: CategoryEditorRoute,
) : BaseViewModel() {
    val draft: StateFlow<CategoryEditorDraft?>
        field = MutableStateFlow(null)

    private fun prepareSubscription(subscription: RawSubscription): RawSubscription {
        if (draft.value == null) reloadDraft(subscription)
        return subscription
    }

    fun reloadDraft(subscription: RawSubscription) {
        val category = subscription.categories.find { it.key == route.categoryKey }
        draft.value = CategoryEditorDraft(
            originalCategory = category?.let { json.encodeToString(it) },
            originalName = category?.name.orEmpty(),
            originalDescription = category?.desc.orEmpty(),
        )
    }

    fun setName(value: String) {
        draft.update { it?.copy(name = value) }
    }

    fun setDescription(value: String) {
        draft.update { it?.copy(description = value) }
    }

    val uiState = RequiredSubscription(
        route.subsId,
        scope
    ).buildUiState(initialValue = ::prepareSubscription) { flowOf(prepareSubscription(it)) }
    private val saveSession = EditorSaveSession<Boolean>()

    suspend fun save(subscription: RawSubscription, name: String, description: String): Boolean =
        saveSession.save {
            SubscriptionRepository.saveCategory(subscription, route.categoryKey, name, description)
        }
}
