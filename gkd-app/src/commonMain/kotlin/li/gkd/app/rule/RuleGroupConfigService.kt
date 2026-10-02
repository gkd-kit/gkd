package li.gkd.app.rule

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.app.launcherAppIdFlow
import li.gkd.app.model.ExcludeData
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionException
import li.gkd.app.subscription.SubscriptionFailureReason
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.db.Db
import li.gkd.db.SubsAppGroupConfig
import li.gkd.db.SubsCategoryConfig
import li.gkd.db.SubsGlobalGroupConfig
import li.gkd.db.SubsGroupConfig
import li.gkd.db.SubscriptionConfigSnapshot
import li.gkd.db.SubscriptionConfigStore
import li.gkd.db.withExclude

data class RuleGroupConfiguration(
    val group: SubsGroupConfig?,
    val snapshot: SubscriptionConfigSnapshot,
)

object RuleGroupConfigService {
    val policy by lazy { RuleGroupPolicy() }
    fun groupConfiguration(target: RuleGroupTarget): Flow<RuleGroupConfiguration> =
        SubscriptionConfigStore.observe().map { snapshot ->
            RuleGroupConfiguration(
                snapshot = snapshot,
                group = when (target) {
                    is RuleGroupTarget.App -> snapshot.appGroupConfigs.find {
                        it.subsId == target.subsId && it.appId == target.appId && it.groupKey == target.groupKey
                    }

                    is RuleGroupTarget.Global -> snapshot.globalGroupConfigs.find {
                        it.subsId == target.subsId && it.groupKey == target.groupKey
                    }
                },
            )
        }.distinctUntilChanged()

    suspend fun setCategorySetting(
        expected: RawSubscription,
        categoryKey: Int,
        setting: CategorySetting,
        expectedSetting: CategorySetting,
    ) = setCategorySettings(expected, mapOf(categoryKey to expectedSetting), setting)

    suspend fun setCategorySettings(
        expected: RawSubscription,
        expectedSettings: Map<Int, CategorySetting>,
        setting: CategorySetting,
    ) = SubscriptionRepository.withSubscriptionSnapshot(expected) { current ->
        require(current.categories.map { it.key }
            .containsAll(expectedSettings.keys)) {
                throw SubscriptionException(SubscriptionFailureReason.CategoryMissing)
            }
        Db.withTransaction {
            val configs = SubscriptionConfigStore.capture().categoryConfigs
                .filter { it.subsId == expected.id }.associateBy { it.categoryKey }
            check(expectedSettings.all { (key, expectedSetting) ->
                CategoryPolicy.setting(configs[key]) == expectedSetting
            }) { throw SubscriptionException(SubscriptionFailureReason.CategoryConfigConflict) }
            expectedSettings.keys.forEach { categoryKey ->
                if (setting == CategorySetting.FollowSubscription) {
                    Db.subsCategoryConfigDao.deleteByCategoryKey(current.id, categoryKey)
                } else {
                    Db.subsCategoryConfigDao.upsert(
                        SubsCategoryConfig(
                            subsId = current.id,
                            categoryKey = categoryKey,
                            enable = when (setting) {
                                CategorySetting.Enabled -> true
                                CategorySetting.Disabled -> false
                                else -> null
                            },
                        )
                    )
                }
            }
        }
    }

    suspend fun replaceExclude(
        target: RuleGroupTarget,
        expected: ExcludeData,
        value: ExcludeData,
        subscription: RawSubscription,
    ) {
        updateExclusions(subscription, target) { current ->
            check(ExcludeData.parse(current.exclude) == expected) {
                throw SubscriptionException(SubscriptionFailureReason.ExclusionConfigConflict)
            }
            current.withExclude(value.stringify())
        }
    }

    suspend fun setActivityExclusion(
        subscription: RawSubscription,
        target: RuleGroupTarget,
        appId: String,
        activityId: String,
        expectedExcluded: Boolean,
        excluded: Boolean,
    ) {
        updateExclusions(subscription, target) { current ->
            val value = ExcludeData.parse(current.exclude)
            val key = appId to activityId
            check((key in value.activityIds) == expectedExcluded) {
                throw SubscriptionException(SubscriptionFailureReason.PageExclusionConflict)
            }
            current.withExclude(
                value.copy(activityIds = if (excluded) value.activityIds + key else value.activityIds - key)
                    .stringify()
            )
        }
    }

    private suspend fun updateExclusions(
        subscription: RawSubscription,
        target: RuleGroupTarget,
        transform: (SubsGroupConfig) -> SubsGroupConfig,
    ) = SubscriptionRepository.withSubscriptionSnapshot(subscription) { current ->
        check(target.subsId == current.id && findGroup(current, target) != null) {
            throw SubscriptionException(SubscriptionFailureReason.RuleMissing)
        }
        updateConfig(target, transform)
    }

    private suspend fun updateConfig(
        target: RuleGroupTarget,
        transform: (SubsGroupConfig) -> SubsGroupConfig,
    ) {
        when (target) {
            is RuleGroupTarget.App -> SubscriptionConfigStore.updateAppGroupConfig(
                target.subsId, target.appId, target.groupKey,
            ) { transform(it) as SubsAppGroupConfig }

            is RuleGroupTarget.Global -> SubscriptionConfigStore.updateGlobalGroupConfig(
                target.subsId, target.groupKey,
            ) { transform(it) as SubsGlobalGroupConfig }
        }
    }

    fun prepare(
        targets: Collection<RuleSwitchTarget>,
        subscriptions: Collection<RawSubscription>,
        snapshot: SubscriptionConfigSnapshot,
    ): RuleSwitchRequest {
        val ids = targets.mapTo(mutableSetOf()) { it.subsId }
        val sources = subscriptions.filter { it.id in ids }.associateBy { it.id }
        check(sources.keys == ids) {
            throw SubscriptionException(SubscriptionFailureReason.SubscriptionUnloadedOrMissing)
        }
        val configIndex = RuleConfigIndex(snapshot)
        return RuleSwitchRequest(sources, targets.distinct().associateWith(configIndex::setting))
    }

    suspend fun apply(request: RuleSwitchRequest, setting: RuleSetting): RuleSwitchResult =
        SubscriptionRepository.withSubscriptionSnapshots(request.subscriptions.values) {
            Db.withTransaction {
                val snapshot = SubscriptionConfigStore.capture()
                val configIndex = RuleConfigIndex(snapshot)
                request.checkCurrent(configIndex)
                val appInfos = AppInfoRepository.snapshot?.apps.orEmpty()
                val systemApps =
                    appInfos.values.filter { it.isSystem }.mapTo(mutableSetOf()) { it.id }
                val launcher = launcherAppIdFlow.value
                val blockedApps = SettingsRepository.blockMatchAppList.value
                var changed = 0
                var unchanged = 0
                var invalid = 0
                var restricted = 0
                request.expected.forEach { (target, expected) ->
                    val subscription = request.subscriptions.getValue(target.subsId)
                    check(configIndex.hasSubscription(target.subsId)) {
                        throw SubscriptionException(SubscriptionFailureReason.SubscriptionMissing)
                    }
                    val groupTarget = when (target) {
                        is RuleSwitchTarget.App -> null
                        is RuleSwitchTarget.AppGroup -> RuleGroupTarget.App(
                            target.subsId,
                            target.appId,
                            target.groupKey
                        )

                        is RuleSwitchTarget.GlobalGroup -> RuleGroupTarget.Global(
                            target.subsId,
                            target.groupKey
                        )

                        is RuleSwitchTarget.GlobalApp -> RuleGroupTarget.Global(
                            target.subsId,
                            target.groupKey,
                            target.appId
                        )
                    }
                    val group = groupTarget?.let {
                        findGroup(subscription, it) ?: throw SubscriptionException(SubscriptionFailureReason.SelectedRulesMissing)
                    }
                    if (target is RuleSwitchTarget.App) {
                        check(subscription.apps.any { it.id == target.appId }) {
                            throw SubscriptionException(SubscriptionFailureReason.SubscriptionAppMissing)
                        }
                    }
                    val state = if (group != null) policy.controlState(
                        subscription, group, groupTarget.pageAppId, snapshot,
                        appInfos[groupTarget.pageAppId],
                        launcher, systemApps, groupTarget.pageAppId in blockedApps, configIndex,
                    ) else null
                    if (setting == RuleSetting.Enabled && state?.canEnable == false) {
                        invalid++
                    } else {
                        if (setting != RuleSetting.Disabled && (state?.restrictions?.isNotEmpty() == true ||
                                    configIndex.subscriptionEnabled(target.subsId) == false ||
                                    (target is RuleSwitchTarget.App && target.appId in blockedApps))
                        ) restricted++
                        if (expected == setting) {
                            unchanged++
                        } else {
                            when (target) {
                                is RuleSwitchTarget.App -> SubscriptionConfigStore.setAppEnabled(
                                    target.subsId,
                                    target.appId,
                                    setting.value
                                )

                                else -> updateConfig(checkNotNull(groupTarget)) {
                                    RuleSwitchPolicy.updateGroup(target, it, setting)
                                }
                            }
                            changed++
                        }
                    }
                }
                RuleSwitchResult(changed, unchanged, invalid, restricted)
            }
        }

    private fun findGroup(
        subscription: RawSubscription,
        target: RuleGroupTarget
    ): RawSubscription.RawGroupProps? = when (target) {
        is RuleGroupTarget.App -> subscription.apps.find { it.id == target.appId }?.groups?.find { it.key == target.groupKey }
        is RuleGroupTarget.Global -> subscription.globalGroups.find { it.key == target.groupKey }
    }
}

data class RuleSwitchRequest(
    val subscriptions: Map<Long, RawSubscription>,
    val expected: Map<RuleSwitchTarget, RuleSetting>,
) {
    fun checkCurrent(configIndex: RuleConfigIndex) {
        check(expected.all { (target, setting) -> configIndex.setting(target) == setting }) {
            throw SubscriptionException(SubscriptionFailureReason.RuleSwitchConflict)
        }
    }
}

data class RuleSwitchResult(
    val changed: Int,
    val unchanged: Int,
    val invalid: Int,
    val restricted: Int
)
