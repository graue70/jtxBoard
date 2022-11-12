/*
 * Copyright (c) Techbee e.U.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.techbee.jtx.ui.list


import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.navigation.NavController
import at.techbee.jtx.R
import at.techbee.jtx.database.*
import at.techbee.jtx.ui.reusable.destinations.DetailDestination
import at.techbee.jtx.ui.settings.SettingsStateHolder


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ListScreen(
    listViewModel: ListViewModel,
    navController: NavController,
    filterBottomSheetState: ModalBottomSheetState,
) {
    val context = LocalContext.current
    val settingsStateHolder = SettingsStateHolder(context)

    listViewModel.toastMessage.value?.let {
        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        listViewModel.toastMessage.value = null
    }

    val list = listViewModel.iCal4List.observeAsState(emptyList())
    // first apply a proper sort order, then group
    val sortedList = when(listViewModel.listSettings.groupBy.value) {
        GroupBy.STATUS -> list.value.sortedBy {
            if(listViewModel.module == Module.TODO && it.percent != 100)
                try { StatusTodo.valueOf(it.status ?: StatusTodo.`NEEDS-ACTION`.name).ordinal } catch (e: java.lang.IllegalArgumentException) { -1 }
            else
                try { StatusJournal.valueOf(it.status ?: StatusJournal.FINAL.name).ordinal } catch (e: java.lang.IllegalArgumentException) { -1 }
        }
        GroupBy.CLASSIFICATION -> list.value.sortedBy {
            try { Classification.valueOf(it.classification ?: Classification.PUBLIC.name).ordinal } catch (e: java.lang.IllegalArgumentException) { -1 }
        }
        GroupBy.PRIORITY -> list.value.sortedBy { it.priority ?: -1 }
        GroupBy.DATE, GroupBy.START -> list.value.sortedBy { it.dtstart ?: 0L }
        GroupBy.DUE ->list.value.sortedBy {
            when {
                it.percent == 100 -> Long.MAX_VALUE-1
                it.due == null -> Long.MAX_VALUE
                else -> it.due
                }
        }
        else -> list.value
    }
    val groupedList = sortedList.groupBy {
        when(listViewModel.listSettings.groupBy.value) {
            GroupBy.STATUS -> {
                if(listViewModel.module == Module.TODO)
                    StatusTodo.getStringResource(context, it.status)
                else
                    StatusJournal.getStringResource(context, it.status)
            }
            GroupBy.CLASSIFICATION -> Classification.getStringResource(context, it.classification)
            GroupBy.PRIORITY -> {
                when(it.priority) {
                    null -> stringArrayResource(id = R.array.priority)[0]
                    in 0..9 -> stringArrayResource(id = R.array.priority)[it.priority!!]
                    else -> it.priority.toString()
                }
            }
            GroupBy.DATE -> it.getDtstartTextInfo(context, true)
            GroupBy.START -> it.getDtstartTextInfo(context, true)
            GroupBy.DUE -> it.getDueTextInfo(context, true)
            else -> { it.module }
        }
    }


    Column {
        when (listViewModel.listSettings.viewMode.value) {
            ViewMode.LIST -> {
                ListScreenList(
                    groupedList = groupedList,
                    subtasksLive = listViewModel.allSubtasksMap,
                    subnotesLive = listViewModel.allSubnotesMap,
                    attachmentsLive = listViewModel.allAttachmentsMap,
                    scrollOnceId = listViewModel.scrollOnceId,
                    listSettings = listViewModel.listSettings,
                    isSubtasksExpandedDefault = settingsStateHolder.settingAutoExpandSubtasks,
                    isSubnotesExpandedDefault = settingsStateHolder.settingAutoExpandSubnotes,
                    isAttachmentsExpandedDefault = settingsStateHolder.settingAutoExpandAttachments,
                    settingShowProgressMaintasks = settingsStateHolder.settingShowProgressForMainTasks,
                    settingShowProgressSubtasks = settingsStateHolder.settingShowProgressForSubTasks,
                    settingProgressIncrement = settingsStateHolder.settingStepForProgress,
                    goToView = { itemId -> navController.navigate(DetailDestination.Detail.getRoute(itemId, false)) },
                    goToEdit = { itemId -> navController.navigate(DetailDestination.Detail.getRoute(itemId, true)) },
                    onProgressChanged = { itemId, newPercent, isLinkedRecurringInstance ->
                        listViewModel.updateProgress(
                            itemId,
                            newPercent,
                            isLinkedRecurringInstance
                        )
                    },
                    onExpandedChanged = { itemId: Long, isSubtasksExpanded: Boolean, isSubnotesExpanded: Boolean, isAttachmentsExpanded: Boolean ->
                        listViewModel.updateExpanded(
                            itemId,
                            isSubtasksExpanded,
                            isSubnotesExpanded,
                            isAttachmentsExpanded
                        )
                    },
                )
            }
            ViewMode.GRID -> {
                ListScreenGrid(
                    list = list,
                    scrollOnceId = listViewModel.scrollOnceId,
                    onProgressChanged = { itemId, newPercent, isLinkedRecurringInstance ->
                        listViewModel.updateProgress(
                            itemId,
                            newPercent,
                            isLinkedRecurringInstance
                        )
                    },
                    goToView = { itemId -> navController.navigate(DetailDestination.Detail.getRoute(itemId, false)) },
                    goToEdit = { itemId -> navController.navigate(DetailDestination.Detail.getRoute(itemId, true)) },
                )
            }
            ViewMode.COMPACT -> {
                ListScreenCompact(
                    groupedList = groupedList,
                    subtasksLive = listViewModel.allSubtasksMap,
                    scrollOnceId = listViewModel.scrollOnceId,
                    listSettings = listViewModel.listSettings,
                    onProgressChanged = { itemId, newPercent, isLinkedRecurringInstance ->
                        listViewModel.updateProgress(
                            itemId,
                            newPercent,
                            isLinkedRecurringInstance
                        )
                    },
                    goToView = { itemId -> navController.navigate(DetailDestination.Detail.getRoute(itemId, false)) },
                    goToEdit = { itemId -> navController.navigate(DetailDestination.Detail.getRoute(itemId, true)) },
                )
            }
            ViewMode.KANBAN -> {
                ListScreenKanban(
                    module = listViewModel.module,
                    list = list,
                    scrollOnceId = listViewModel.scrollOnceId,
                    onProgressChanged = { itemId, newPercent, isLinkedRecurringInstance, scrollOnce ->
                        listViewModel.updateProgress(
                            itemId,
                            newPercent,
                            isLinkedRecurringInstance,
                            scrollOnce
                        )
                    },
                    onStatusChanged = { itemId, newStatus, isLinkedRecurringInstance, scrollOnce ->
                        listViewModel.updateStatusJournal(
                            itemId,
                            newStatus,
                            isLinkedRecurringInstance,
                            scrollOnce
                        )
                    },
                    goToView = { itemId -> navController.navigate(DetailDestination.Detail.getRoute(itemId, false)) },
                    goToEdit = { itemId -> navController.navigate(DetailDestination.Detail.getRoute(itemId, true)) },
                )
            }
        }
    }

    ModalBottomSheetLayout(
        sheetState = filterBottomSheetState,
        sheetContent = {
            ListOptionsBottomSheet(
                module = listViewModel.module,
                listSettings = listViewModel.listSettings,
                allCollectionsLive = listViewModel.allWriteableCollections,
                allCategoriesLive = listViewModel.allCategories,
                onListSettingsChanged = { listViewModel.updateSearch(saveListSettings = true) }
            )
        },
        sheetBackgroundColor = MaterialTheme.colorScheme.surface,
        sheetContentColor = MaterialTheme.colorScheme.contentColorFor(MaterialTheme.colorScheme.surface)
    ) { }
}
