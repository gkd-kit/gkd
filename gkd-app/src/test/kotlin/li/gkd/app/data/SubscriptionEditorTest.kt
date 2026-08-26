package li.songe.gkd.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionEditorTest {

    @Test
    fun updatesArbitrarySubscriptionAndNestedFieldsInOneSnapshot() {
        val source = createSubscription(
            apps = listOf(RawSubscription.RawApp("app.id", "Old name")),
            categories = listOf(RawSubscription.RawCategory(0, "Old", null, null)),
        )

        val edited = source.edit {
            update { copy(name = "Edited", author = "Author") }
            assertTrue(updateApp("app.id") { copy(name = "New name") })
            assertTrue(updateCategory(0) { copy(name = "New category", desc = "Desc") })
        }

        assertEquals("Edited", edited.name)
        assertEquals("Author", edited.author)
        assertEquals("New name", edited.apps.single().name)
        assertEquals("New category", edited.categories.single().name)
        assertEquals("Desc", edited.categories.single().desc)
    }

    @Test
    fun missingStrictNodeIsNotCreated() {
        val source = createSubscription()

        val edited = source.edit {
            assertFalse(updateApp("missing") { copy(name = "Unexpected") })
            assertFalse(updateCategory(1) { copy(name = "Unexpected") })
        }

        assertSame(source, edited)
    }

    @Test
    fun removingLastAppGroupCanRemoveItsApp() {
        val source = RawSubscription.parse(
            """
            {
              id: -2,
              name: 'Local',
              version: 0,
              apps: [{
                id: 'app.id',
                groups: [{ key: 0, name: 'Rule', rules: [] }],
              }],
            }
            """.trimIndent(),
        )

        val edited = source.edit {
            val removed = removeAppGroups("app.id", removeAppIfEmpty = true) { it.key == 0 }
            assertEquals(1, removed.size)
        }

        assertTrue(edited.apps.isEmpty())
    }

    @Test
    fun replacesAndRemovesAppAndGlobalGroupsByKey() {
        val source = RawSubscription.parse(
            """
            {
              id: -2,
              name: 'Local',
              version: 0,
              globalGroups: [{ key: 1, name: 'Global', rules: [] }],
              apps: [{
                id: 'app.id',
                groups: [{ key: 2, name: 'App', rules: [] }],
              }],
            }
            """.trimIndent(),
        )

        val edited = source.edit {
            val globalGroup = source.globalGroups.single()
            replaceGlobalGroup(
                groupKey = globalGroup.key,
                expectedGroup = globalGroup,
                newGroup = globalGroup.copy(name = "Edited global"),
            )
            val app = source.apps.single()
            val appGroup = app.groups.single()
            replaceAppGroup(
                targetApp = app,
                groupKey = appGroup.key,
                expectedGroup = appGroup,
                newGroup = appGroup.copy(name = "Edited app"),
            )
        }

        assertEquals("Edited global", edited.globalGroups.single().name)
        assertEquals("Edited app", edited.apps.single().groups.single().name)

        val removed = edited.edit {
            assertEquals(1, removeGlobalGroups { it.key == 1 }.size)
            assertEquals(1, removeAppGroups("app.id") { it.key == 2 }.size)
        }

        assertTrue(removed.globalGroups.isEmpty())
        assertTrue(removed.apps.single().groups.isEmpty())
    }

    @Test
    fun subscriptionIdCannotBeChanged() {
        val source = createSubscription()

        assertThrows(IllegalArgumentException::class.java) {
            source.edit { update { copy(id = 1) } }
        }
    }

    @Test
    fun appendingGroupsCreatesMissingAppAndNormalizesConflictingKeys() {
        val source = createSubscription(
            apps = listOf(
                RawSubscription.RawApp(
                    id = "existing.app",
                    name = null,
                    groups = listOf(createAppGroup(0, "Existing")),
                ),
            ),
        )

        val edited = source.edit {
            appendAppGroups(
                source.apps.first { it.id == "existing.app" },
                listOf(createAppGroup(0, "First"), createAppGroup(1, "Second")),
            )
            appendAppGroups(
                RawSubscription.RawApp("new.app", "New app"),
                listOf(createAppGroup(0, "New")),
            )
        }

        assertEquals(
            listOf(0, 1, 2),
            edited.apps.first { it.id == "existing.app" }.groups.map { it.key },
        )
        val newApp = edited.apps.first { it.id == "new.app" }
        assertEquals("New app", newApp.name)
        assertEquals("New", newApp.groups.single().name)
    }

    @Test
    fun duplicateGroupNamesAreRejectedWhenAppending() {
        val source = createSubscription(
            apps = listOf(
                RawSubscription.RawApp(
                    id = "app.id",
                    name = null,
                    groups = listOf(createAppGroup(0, "Duplicate")),
                ),
            ),
        )

        val error = assertThrows(IllegalStateException::class.java) {
            source.edit {
                appendAppGroups(
                    source.apps.single(),
                    listOf(createAppGroup(1, "Duplicate")),
                )
            }
        }

        assertEquals("已存在同名「Duplicate」规则", error.message)
    }

    @Test
    fun replacingGroupCreatesMissingAppAndRejectsStaleEdits() {
        val originalGroup = createAppGroup(0, "Original")
        val replacement = createAppGroup(0, "Replacement")
        val missingAppEdited = createSubscription().edit {
            replaceAppGroup(
                targetApp = RawSubscription.RawApp("missing.app", "Missing app"),
                groupKey = 0,
                expectedGroup = originalGroup,
                newGroup = replacement,
            )
        }

        assertEquals("missing.app", missingAppEdited.apps.single().id)
        assertEquals("Missing app", missingAppEdited.apps.single().name)
        assertEquals("Replacement", missingAppEdited.apps.single().groups.single().name)

        val changedGroup = createAppGroup(0, "Changed elsewhere")
        val changedSource = createSubscription(
            apps = listOf(RawSubscription.RawApp("app.id", null, listOf(changedGroup))),
        )
        val error = assertThrows(IllegalStateException::class.java) {
            changedSource.edit {
                replaceAppGroup(
                    targetApp = changedSource.apps.single(),
                    groupKey = 0,
                    expectedGroup = originalGroup,
                    newGroup = replacement,
                )
            }
        }

        assertEquals("规则已发生变化，请重新编辑", error.message)
    }

    private fun createSubscription(
        apps: List<RawSubscription.RawApp> = emptyList(),
        categories: List<RawSubscription.RawCategory> = emptyList(),
    ) = RawSubscription(
        id = -2,
        name = "Local",
        version = 0,
        apps = apps,
        categories = categories,
    )

    private fun createAppGroup(
        key: Int,
        name: String,
    ): RawSubscription.RawAppGroup = RawSubscription.parse(
        """
        {
          id: -2,
          name: 'Local',
          version: 0,
          apps: [{
            id: 'app.id',
            groups: [{ key: $key, name: '$name', rules: [] }],
          }],
        }
        """.trimIndent(),
    ).apps.single().groups.single()
}
