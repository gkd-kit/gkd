package li.gkd.app.ui.subscription

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import li.gkd.app.resources.*
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionEditResult
import li.gkd.app.subscription.SubscriptionJson.json
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.ui.navigation.CategoryEditorRoute
import li.gkd.app.ui.page.EditorActionsState
import li.gkd.app.ui.page.EditorSaveResult
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.app.util.ToastUtils
import org.jetbrains.compose.resources.getString

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
    val editorActions = EditorActionsState(scope)

    val draft: StateFlow<CategoryEditorDraft?>
        field = MutableStateFlow(null)

    private fun prepareSubscription(subscription: RawSubscription): RawSubscription {
        if (draft.value == null) reloadDraft(subscription)
        return subscription
    }

    fun reloadDraft(subscription: RawSubscription) {
        val category = subscription.categories.find { it.key == route.categoryKey }
        draft.value =
            CategoryEditorDraft(
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

    val uiState =
        RequiredSubscription(
                route.subsId,
                scope,
            )
            .buildUiState(initialValue = ::prepareSubscription) { flowOf(prepareSubscription(it)) }
    private val saveSession = EditorSaveSession<EditorSaveResult> { it == EditorSaveResult.Saved }

    suspend fun save(
        subscription: RawSubscription,
        name: String,
        description: String,
    ): EditorSaveResult = saveSession.save {
        when (
            val result =
                SubscriptionRepository.saveCategory(
                    subscription,
                    route.categoryKey,
                    name,
                    description,
                )
        ) {
            is SubscriptionEditResult.Rejected ->
                EditorSaveResult.Rejected(result.errorMessage.resolve())
            is SubscriptionEditResult.Success -> {
                ToastUtils.show(
                    getString(
                        if (!result.value) Res.string.unchanged
                        else if (route.categoryKey == null) Res.string.add_success
                        else Res.string.update_success,
                    ),
                )
                EditorSaveResult.Saved
            }
        }
    }
}
