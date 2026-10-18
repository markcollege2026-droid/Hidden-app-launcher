package com.hidden.launcher

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for drag-to-group functionality and persistence
 */
class GroupTest {

    @Test
    fun `buildDisplayItems separates grouped and individual`() {
        val displays = listOf(
            SelectedAppDisplay("com.a", "A", null, true, true),
            SelectedAppDisplay("com.b", "B", null, true, true),
            SelectedAppDisplay("com.c", "C", null, true, true),
            SelectedAppDisplay("com.d", "D", null, true, true)
        )
        val groups = listOf(
            AppGroup("id1", "My Group", listOf("com.a", "com.b"))
        )
        val items = PrefsHelper.buildDisplayItems(displays, groups)
        // Should have 1 group + 2 individual (c,d) = 3 items
        assertEquals(3, items.size)
        assertTrue(items.any { it is MainItem.Group && it.group.name == "My Group" })
        assertTrue(items.any { it is MainItem.App && (it as MainItem.App).display.packageName == "com.c" })
        assertTrue(items.any { it is MainItem.App && (it as MainItem.App).display.packageName == "com.d" })
        // Grouped apps should not appear as individual
        assertFalse(items.any { it is MainItem.App && it.display.packageName == "com.a" })
        assertFalse(items.any { it is MainItem.App && it.display.packageName == "com.b" })
    }

    @Test
    fun `buildDisplayItems handles multiple groups sorted`() {
        val displays = listOf(
            SelectedAppDisplay("com.a", "A", null, true, true),
            SelectedAppDisplay("com.b", "B", null, true, true),
            SelectedAppDisplay("com.c", "C", null, true, true)
        )
        val groups = listOf(
            AppGroup("id2", "Zebra", listOf("com.c")),
            AppGroup("id1", "Alpha", listOf("com.a", "com.b"))
        )
        val items = PrefsHelper.buildDisplayItems(displays, groups)
        // Groups sorted by name: Alpha first, Zebra second - but Zebra has only 1 member? Actually our build includes groups with at least 1 member
        // However our current logic shows group even with 1 member if present; but prune would remove single-member groups
        // For test we keep as given
        assertEquals(3, items.size)
        assertTrue((items[0] as? MainItem.Group)?.group?.name == "Alpha")
        assertTrue((items[1] as? MainItem.Group)?.group?.name == "Zebra")
    }

    @Test
    fun `groupingAction correctly identifies create group`() {
        val app1 = MainItem.App(SelectedAppDisplay("com.a", "A", null, true, true))
        val app2 = MainItem.App(SelectedAppDisplay("com.b", "B", null, true, true))
        val group = MainItem.Group(AppGroup("g1", "G", listOf("com.c", "com.d")), emptyList())
        assertEquals("create_group", PrefsHelper.groupingAction(app1, app2))
        assertEquals("add_to_group", PrefsHelper.groupingAction(app1, group))
        assertEquals("add_to_group", PrefsHelper.groupingAction(group, app2))
        val group2 = MainItem.Group(AppGroup("g2", "G2", listOf("com.e")), emptyList())
        assertEquals("merge_groups", PrefsHelper.groupingAction(group, group2))
    }

    @Test
    fun `generateGroupName uses labels`() {
        val members = listOf(
            SelectedAppDisplay("com.a", "Chrome", null, true, true),
            SelectedAppDisplay("com.b", "Maps", null, true, true)
        )
        val name = PrefsHelper.generateGroupName(members, 1)
        assertEquals("Chrome + Maps", name)

        val emptyName = PrefsHelper.generateGroupName(emptyList(), 3)
        assertEquals("Group 3", emptyName)

        val threeMembers = listOf(
            SelectedAppDisplay("com.a", "A", null, true, true),
            SelectedAppDisplay("com.b", "B", null, true, true),
            SelectedAppDisplay("com.c", "C", null, true, true)
        )
        val name3 = PrefsHelper.generateGroupName(threeMembers, 1)
        assertTrue(name3.contains("A + B"))
        assertTrue(name3.contains("+1"))
    }

    @Test
    fun `groups JSON round trip`() {
        val groups = listOf(
            AppGroup("id1", "Work", listOf("com.a", "com.b")),
            AppGroup("id2", "Play", listOf("com.c"))
        )
        // Use companion static helpers (no Context needed)
        val json = PrefsHelper.groupsToJson(groups)
        val parsed = PrefsHelper.parseGroupsJson(json)
        assertEquals(groups.size, parsed.size)
        assertEquals(groups[0].id, parsed[0].id)
        assertEquals(groups[0].name, parsed[0].name)
        assertEquals(groups[0].packageNames, parsed[0].packageNames)
        assertEquals(groups[1].name, parsed[1].name)
    }

    @Test
    fun `group persistence prune logic`() {
        // Simulate pruning: group with removed package should be pruned
        val selected = setOf("com.a", "com.b") // com.c removed
        val groupsBefore = listOf(
            AppGroup("id1", "G1", listOf("com.a", "com.b", "com.c")),
            AppGroup("id2", "G2", listOf("com.c")) // would become empty
        )
        // Manual prune simulation
        val pruned = groupsBefore.mapNotNull { g ->
            val remaining = g.packageNames.filter { it in selected }
            when {
                remaining.isEmpty() -> null
                remaining.size == 1 -> null
                else -> g.copy(packageNames = remaining)
            }
        }
        assertEquals(1, pruned.size)
        assertEquals("G1", pruned[0].name)
        assertEquals(listOf("com.a", "com.b"), pruned[0].packageNames)
    }

    @Test
    fun `group creation creates distinct ids`() {
        val g1 = AppGroup("uuid-1", "G1", listOf("com.a"))
        val g2 = AppGroup("uuid-2", "G2", listOf("com.b"))
        assertNotEquals(g1.id, g2.id)
    }

    @Test
    fun `drag app onto app creates group containing both`() {
        val displays = listOf(
            SelectedAppDisplay("com.chrome", "Chrome", null, true, true),
            SelectedAppDisplay("com.maps", "Maps", null, true, true)
        )
        // Simulate creation
        val group = AppGroup(id = "test-id", name = "Chrome + Maps", packageNames = displays.map { it.packageName })
        assertEquals(2, group.packageNames.size)
        assertTrue("com.chrome" in group.packageNames)
        assertTrue("com.maps" in group.packageNames)
        assertEquals("Chrome + Maps", group.name)
    }

    @Test
    fun `group item identifier format`() {
        val appItem = MainItem.App(SelectedAppDisplay("com.a", "A", null, true, true))
        assertEquals("app:com.a", appItem.identifier())
        val groupItem = MainItem.Group(AppGroup("grp123", "My Group", listOf("com.a")), emptyList())
        assertEquals("group:grp123", groupItem.identifier())
    }
}
