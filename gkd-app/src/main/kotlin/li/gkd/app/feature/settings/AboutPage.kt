package li.gkd.app.feature.settings

import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.MainViewModel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.text.UiStrings
import li.gkd.app.META
import li.gkd.app.R
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.store.AppStore
import li.gkd.app.ui.share.LocalDarkTheme
import li.gkd.app.ui.style.itemPadding
import li.gkd.app.ui.style.titleItemPadding
import li.gkd.app.util.ISSUES_URL
import li.gkd.app.util.REPOSITORY_URL
import li.gkd.app.util.ShortUrlSet
import li.gkd.app.util.UpdateChannelOption
import li.gkd.app.util.findOption
import li.gkd.app.ui.share.launchUiAction
import li.gkd.app.ui.share.launchUi
import li.gkd.app.util.TimeUtils.throttle
import li.gkd.app.util.ToastUtils.toast
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkRotatingLoadingIcon
import li.gkd.app.ui.component.GkSettingItem
import li.gkd.app.ui.component.GkTextMenu
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.icon.GkAnimatedLogoIcon

@Serializable
data object AboutRoute : NavKey

@Composable
fun AboutPage() {
    val mainVm = MainViewModel.requireCurrent()
    var showVersionInfoDialog by rememberSaveable { mutableStateOf(false) }
    var showShareAppDialog by rememberSaveable { mutableStateOf(false) }
    val store by storeFlow.collectAsStateWithLifecycle()
    val updateChannel = UpdateChannelOption.objects.findOption(store.updateChannel)

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    GkIconButton(
                        imageVector = GkIcons.ArrowBack,
                        onClick = {
                            mainVm.popPage()
                        },
                    )
                },
                title = { Text(text = UiStrings.about_title) },
                actions = {
                    GkIconButton(
                        imageVector = GkIcons.Share,
                        onClick = { showShareAppDialog = true },
                    )
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GkAnimatedLogoIcon(
                    tint = colorResource(
                        if (LocalDarkTheme.current) R.color.better_white else R.color.better_black
                    ),
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = throttle { toast(UiStrings.about_easter_egg) }
                        )
                        .fillMaxWidth(0.33f)
                        .aspectRatio(1f)
                )
                Column(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(onClick = { showVersionInfoDialog = true })
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = META.appName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = META.versionName,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            GkSettingItem(
                imageVector = null,
                title = UiStrings.source_code,
                onClick = {
                    mainVm.openUrl(REPOSITORY_URL)
                },
            )
            if (META.isGkdChannel) {
                GkSettingItem(
                    imageVector = null,
                    title = UiStrings.donate,
                    onClick = {
                        mainVm.navigateWebPage(ShortUrlSet.URL10)
                    },
                )
            }
            GkSettingItem(
                imageVector = null,
                title = UiStrings.terms_of_use,
                onClick = {
                    mainVm.navigateWebPage(ShortUrlSet.URL12)
                },
            )
            GkSettingItem(
                imageVector = null,
                title = UiStrings.privacy_policy,
                onClick = {
                    mainVm.navigateWebPage(ShortUrlSet.URL11)
                },
            )

            FeedbackSection()
            GkSettingItem(
                title = UiStrings.logs_export,
                imageVector = GkIcons.Share,
                onClick = {
                    mainVm.shareLog.show()
                }
            )
            if (mainVm.updateStatus != null) {
                Text(
                    text = UiStrings.action_update,
                    modifier = Modifier.titleItemPadding(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                GkTextMenu(
                    title = UiStrings.update_channel,
                    option = updateChannel
                ) {
                    if (mainVm.updateStatus.checkUpdatingFlow.value) return@GkTextMenu
                    if (it.value == UpdateChannelOption.Beta.value) {
                        mainVm.scope.launchUi {
                            if (!mainVm.dialogRequests.confirm(
                                title = UiStrings.version_channel,
                                text = UiStrings.beta_channel_warning,
                            )) return@launchUi
                            AppStore.updateSettings { settings ->
                                settings.copy(updateChannel = it.value)
                            }
                        }
                    } else {
                        AppStore.updateSettings { settings ->
                            settings.copy(updateChannel = it.value)
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .clickable(
                            onClick = throttle {
                                mainVm.updateStatus.checkUpdate(true)
                            }
                        )
                        .fillMaxWidth()
                        .itemPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = UiStrings.update_check,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    GkRotatingLoadingIcon(loading = mainVm.updateStatus.checkUpdatingFlow.collectAsStateWithLifecycle().value)
                }
            }
            GkPageBottomSpace()
        }
    }

    AboutDialogs(
        showVersionInfo = showVersionInfoDialog,
        onDismissVersionInfo = { showVersionInfoDialog = false },
        showShareApp = showShareAppDialog,
        onDismissShareApp = { showShareAppDialog = false },
    )
}

@Composable
private fun FeedbackSection() {
    val mainVm = MainViewModel.requireCurrent()
    val primaryColor = MaterialTheme.colorScheme.primary
    Text(
        text = UiStrings.feedback_title,
        modifier = Modifier.titleItemPadding(),
        style = MaterialTheme.typography.titleSmall,
        color = primaryColor,
    )
    Column(
        modifier = Modifier
            .clickable(onClick = throttle(mainVm.scope.launchUiAction {
                val noticeText = buildAnnotatedString {
                    val highlightStyle = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                    )
                    append(UiStrings.feedback_thanks_prefix)
                    withStyle(style = highlightStyle) {
                        append(UiStrings.feedback_scope)
                    }
                    append("\n\n")
                    append(UiStrings.feedback_subscription_notice)
                    withStyle(style = highlightStyle) {
                        append(UiStrings.feedback_app_issue_prefix)
                    }
                    append(UiStrings.feedback_continue_suffix)
                }
                if (!mainVm.dialogRequests.confirm(
                    title = UiStrings.feedback_notice,
                    text = noticeText,
                    confirmText = UiStrings.action_continue,
                    dismissOnRequest = true,
                )) return@launchUiAction
                mainVm.openUrl(ISSUES_URL)
            }))
            .fillMaxWidth()
            .itemPadding()
    ) {
        Text(
            text = UiStrings.feedback_report,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
