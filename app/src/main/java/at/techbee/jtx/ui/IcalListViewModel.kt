/*
 * Copyright (c) Techbee e.U.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.techbee.jtx.ui

import android.accounts.Account
import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.*
import androidx.sqlite.db.SimpleSQLiteQuery
import at.techbee.jtx.R
import at.techbee.jtx.database.*
import at.techbee.jtx.database.ICalObject.Factory.TZ_ALLDAY
import at.techbee.jtx.database.properties.*
import at.techbee.jtx.database.relations.ICal4ListWithRelatedto
import at.techbee.jtx.database.relations.ICalEntity
import at.techbee.jtx.database.views.ICal4List
import at.techbee.jtx.database.views.VIEW_NAME_ICAL4LIST
import at.techbee.jtx.util.DateTimeUtils
import at.techbee.jtx.util.SyncUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit


open class IcalListViewModel(application: Application) : AndroidViewModel(application) {

    private var database: ICalDatabaseDao = ICalDatabase.getInstance(application).iCalDatabaseDao

    var searchModule: String = Module.JOURNAL.name
    var searchText: String = ""
    var searchCategories: MutableList<String> = mutableListOf()
    var searchOrganizer: MutableList<String> = mutableListOf()
    var searchStatusJournal: MutableList<StatusJournal> = mutableListOf()
    var searchStatusTodo: MutableList<StatusTodo> = mutableListOf()
    var searchClassification: MutableList<Classification> = mutableListOf()
    var searchCollection: MutableList<String> = mutableListOf()
    var searchAccount: MutableList<String> = mutableListOf()
    var isExcludeDone: Boolean = false
    var isFilterOverdue: Boolean = false
    var isFilterDueToday: Boolean = false
    var isFilterDueTomorrow: Boolean = false
    var isFilterDueFuture: Boolean = false

    var searchSettingShowAllSubtasksInTasklist: Boolean = false
    var searchSettingShowAllSubnotesInNoteslist: Boolean = false
    var searchSettingShowAllSubjournalsinJournallist: Boolean = false


    private var listQueryJournals: MutableLiveData<SimpleSQLiteQuery> = MutableLiveData<SimpleSQLiteQuery>()
    private var listQueryNotes: MutableLiveData<SimpleSQLiteQuery> = MutableLiveData<SimpleSQLiteQuery>()
    private var listQueryTodos: MutableLiveData<SimpleSQLiteQuery> = MutableLiveData<SimpleSQLiteQuery>()
    var iCal4ListJournals: LiveData<List<ICal4ListWithRelatedto>> = Transformations.switchMap(listQueryJournals) {
        database.getIcalObjectWithRelatedto(it)
    }
    var iCal4ListNotes: LiveData<List<ICal4ListWithRelatedto>> = Transformations.switchMap(listQueryNotes) {
        database.getIcalObjectWithRelatedto(it)
    }
    var iCal4ListTodos: LiveData<List<ICal4ListWithRelatedto>> = Transformations.switchMap(listQueryTodos) {
        database.getIcalObjectWithRelatedto(it)
    }

        // TODO maybe retrieve all subtasks only when subtasks are needed!
    val allSubtasks: LiveData<List<ICal4List?>> = database.getAllSubtasks()

    val allCategories = database.getAllCategories()   // filter FragmentDialog
    val allCollections = database.getAllCollections() // filter FragmentDialog

    val allRemoteCollections = database.getAllRemoteCollections()
    val allWriteableCollectionsVJournal = database.getAllWriteableVJOURNALCollections()
    val allWriteableCollectionsVTodo = database.getAllWriteableVTODOCollections()

    val quickInsertedEntity = MutableLiveData<ICalEntity?>(null)
    val directEditEntity = MutableLiveData<ICalEntity?>(null)
    val scrollOnceId = MutableLiveData<Long?>(null)

    val isSynchronizing = MutableLiveData(false)


    private fun constructQuery(): SimpleSQLiteQuery {

        val args = arrayListOf<String>()

// Beginning of query string
        var queryString = "SELECT DISTINCT $VIEW_NAME_ICAL4LIST.* FROM $VIEW_NAME_ICAL4LIST "
        if(searchCategories.isNotEmpty())
            queryString += "LEFT JOIN $TABLE_NAME_CATEGORY ON $VIEW_NAME_ICAL4LIST.$COLUMN_ID = $TABLE_NAME_CATEGORY.$COLUMN_CATEGORY_ICALOBJECT_ID "
        if(searchOrganizer.isNotEmpty())
            queryString += "LEFT JOIN $TABLE_NAME_ORGANIZER ON $VIEW_NAME_ICAL4LIST.$COLUMN_ID = $TABLE_NAME_ORGANIZER.$COLUMN_ORGANIZER_ICALOBJECT_ID "
        if(searchCollection.isNotEmpty() || searchAccount.isNotEmpty())
            queryString += "LEFT JOIN $TABLE_NAME_COLLECTION ON $VIEW_NAME_ICAL4LIST.$COLUMN_ICALOBJECT_COLLECTIONID = $TABLE_NAME_COLLECTION.$COLUMN_COLLECTION_ID "  // +
        //     "LEFT JOIN vattendees ON icalobject._id = vattendees.icalObjectId " +
        //     "LEFT JOIN vorganizer ON icalobject._id = vorganizer.icalObjectId " +
        //     "LEFT JOIN vRelatedto ON icalobject._id = vRelatedto.icalObjectId "

        // First query parameter Component must always be present!
        queryString += "WHERE $COLUMN_MODULE = ? "
        args.add(searchModule)

        // Query for the given text search from the action bar
        if (searchText.isNotEmpty() && searchText.length >= 2) {
            queryString += "AND ($VIEW_NAME_ICAL4LIST.$COLUMN_SUMMARY LIKE ? OR $VIEW_NAME_ICAL4LIST.$COLUMN_DESCRIPTION LIKE ?) "
            args.add(searchText)
            args.add(searchText)
        }

        // Query for the passed filter criteria from VJournalFilterFragment
        if (searchCategories.size > 0) {
            queryString += "AND $TABLE_NAME_CATEGORY.$COLUMN_CATEGORY_TEXT IN ("
            searchCategories.forEach {
                queryString += "?,"
                args.add(it)
            }
            queryString = queryString.removeSuffix(",")      // remove the last comma
            queryString += ") "
        }

        // Query for the passed filter criteria from VJournalFilterFragment
        if (searchOrganizer.size > 0) {
            queryString += "AND $TABLE_NAME_ORGANIZER.$COLUMN_ORGANIZER_CALADDRESS IN ("
            searchOrganizer.forEach {
                queryString += "?,"
                args.add(it)
            }
            queryString = queryString.removeSuffix(",")      // remove the last comma
            queryString += ") "
        }

        // Query for the passed filter criteria from FilterFragment
        if (searchStatusJournal.size > 0 && (searchModule == Module.JOURNAL.name || searchModule == Module.NOTE.name)) {
            queryString += "AND $COLUMN_STATUS IN ("
            searchStatusJournal.forEach {
                queryString += "?,"
                args.add(it.toString())
            }
            queryString = queryString.removeSuffix(",")      // remove the last comma
            queryString += ") "
        }

        // Query for the passed filter criteria from FilterFragment
        if (searchStatusTodo.size > 0 && searchModule == Module.TODO.name) {
            queryString += "AND $COLUMN_STATUS IN ("
            searchStatusTodo.forEach {
                queryString += "?,"
                args.add(it.toString())
            }
            queryString = queryString.removeSuffix(",")      // remove the last comma
            queryString += ") "
        }

        if (isExcludeDone)
            queryString += "AND $COLUMN_PERCENT IS NOT 100 "

        val dueQuery = mutableListOf<String>()
        if (isFilterOverdue)
            dueQuery.add("$COLUMN_DUE < ${System.currentTimeMillis()}")
        if (isFilterDueToday)
            dueQuery.add("$COLUMN_DUE BETWEEN ${DateTimeUtils.getTodayAsLong()} AND ${DateTimeUtils.getTodayAsLong()+ TimeUnit.DAYS.toMillis(1)-1}")
        if (isFilterDueTomorrow)
            dueQuery.add("$COLUMN_DUE BETWEEN ${DateTimeUtils.getTodayAsLong()+ TimeUnit.DAYS.toMillis(1)} AND ${DateTimeUtils.getTodayAsLong() + TimeUnit.DAYS.toMillis(2)-1}")
        if (isFilterDueFuture)
            dueQuery.add("$COLUMN_DUE > ${System.currentTimeMillis()}")
        if(dueQuery.isNotEmpty())
            queryString += " AND (${dueQuery.joinToString(separator = " OR ")}) "

        // Query for the passed filter criteria from FilterFragment
        if (searchClassification.size > 0) {
            queryString += "AND $COLUMN_CLASSIFICATION IN ("
            searchClassification.forEach {
                queryString += "?,"
                args.add(it.toString())
            }
            queryString = queryString.removeSuffix(",")      // remove the last comma
            queryString += ") "
        }


        // Query for the passed filter criteria from FilterFragment
        if (searchCollection.size > 0) {
            queryString += "AND $TABLE_NAME_COLLECTION.$COLUMN_COLLECTION_DISPLAYNAME IN ("
            searchCollection.forEach {
                queryString += "?,"
                args.add(it)
            }
            queryString = queryString.removeSuffix(",")      // remove the last comma
            queryString += ") "
        }

        // Query for the passed filter criteria from FilterFragment
        if (searchAccount.isNotEmpty()) {
            queryString += "AND $TABLE_NAME_COLLECTION.$COLUMN_COLLECTION_ACCOUNT_NAME IN ("
            searchAccount.forEach {
                queryString += "?,"
                args.add(it)
            }
            queryString = queryString.removeSuffix(",")      // remove the last comma
            queryString += ") "
        }

        // Exclude items that are Child items by checking if they appear in the linkedICalObjectId of relatedto!
        //queryString += "AND $VIEW_NAME_ICAL4LIST.$COLUMN_ID NOT IN (SELECT $COLUMN_RELATEDTO_LINKEDICALOBJECT_ID FROM $TABLE_NAME_RELATEDTO) "
        when (searchModule) {
            Module.TODO.name -> {
                // we exclude all Children of Tasks from the List, as they never should appear as main tasks (they will later be added as subtasks in the observer)
                queryString += "AND $VIEW_NAME_ICAL4LIST.isChildOfTodo = 0 "

                // if the user did NOT set the option to see all tasks that are subtasks of Notes and Journals, then we exclude them here as well
                if (!searchSettingShowAllSubtasksInTasklist)
                    queryString += "AND $VIEW_NAME_ICAL4LIST.isChildOfJournal = 0 AND $VIEW_NAME_ICAL4LIST.isChildOfNote = 0 "

            }
            Module.NOTE.name -> {
                queryString += "AND $VIEW_NAME_ICAL4LIST.isChildOfNote = 0 "

                if (!searchSettingShowAllSubnotesInNoteslist)
                    queryString += "AND $VIEW_NAME_ICAL4LIST.isChildOfJournal = 0 AND $VIEW_NAME_ICAL4LIST.isChildOfTodo = 0 "

            }
            Module.JOURNAL.name -> {
                queryString += "AND $VIEW_NAME_ICAL4LIST.isChildOfJournal = 0 "

                if (!searchSettingShowAllSubjournalsinJournallist)
                    queryString += "AND $VIEW_NAME_ICAL4LIST.isChildOfNote = 0 AND $VIEW_NAME_ICAL4LIST.isChildOfTodo = 0 "
            }
        }

        when (searchModule) {
            Module.JOURNAL.name -> queryString += "ORDER BY $COLUMN_DTSTART ASC, $COLUMN_CREATED ASC "
            Module.NOTE.name -> queryString += "ORDER BY $COLUMN_LAST_MODIFIED DESC, $COLUMN_CREATED ASC "
            Module.TODO.name -> queryString += "ORDER BY $COLUMN_DUE ASC, $COLUMN_CREATED ASC "
        }
        //Log.println(Log.INFO, "queryString", queryString)
        //Log.println(Log.INFO, "queryStringArgs", args.joinToString(separator = ", "))

        return SimpleSQLiteQuery(queryString, args.toArray())
    }

    /**
     * updates the search by constructing a new query and by posting the
     * new query in the listQuery variable. This can trigger an
     * observer in the fragment.
     */
    fun updateSearch() {
        val newQuery = constructQuery()
            when(searchModule) {
                Module.JOURNAL.name -> listQueryJournals.postValue(newQuery)
                Module.NOTE.name -> listQueryNotes.postValue(newQuery)
                Module.TODO.name -> listQueryTodos.postValue(newQuery)
        }
    }


    /**
     * Clears all search criteria (except for module) and updates the search
     */
    fun clearFilter() {
        searchCategories.clear()
        searchOrganizer.clear()
        searchStatusJournal.clear()
        searchStatusTodo.clear()
        searchClassification.clear()
        searchCollection.clear()
        searchAccount.clear()
        isExcludeDone = false
        isFilterOverdue = false
        isFilterDueToday = false
        isFilterDueTomorrow = false
        isFilterDueFuture = false
        updateSearch()
    }


    fun updateProgress(itemId: Long, newPercent: Int, isLinkedRecurringInstance: Boolean) {

        viewModelScope.launch(Dispatchers.IO) {
            val currentItem = database.getICalObjectById(itemId)
            ICalObject.makeRecurringException(currentItem!!, database)
            val item = database.getSync(itemId)!!.property
            item.setUpdatedProgress(newPercent)
            database.update(item)
            SyncUtil.notifyContentObservers(getApplication())
        }
        if(isLinkedRecurringInstance)
            Toast.makeText(getApplication(), R.string.toast_item_is_now_recu_exception, Toast.LENGTH_LONG).show()

    }

    /**
     * This function takes a list of Accounts and checks, if there are any collections that are not in the
     * account list anymore. In this case the collections of the missing account in the list are also
     * locally deleted.
     */
    fun removeDeletedAccounts(allDavx5Accounts: Array<Account>) {

        Log.d("checkForDeletedAccounts", "Found accounts: $allDavx5Accounts")
        allRemoteCollections.value?.forEach { collection ->

            // The Test account type should not be deleted, otherwise the tests will fail!
            if(collection.accountType == ICalCollection.TEST_ACCOUNT_TYPE )
                return@forEach

            val found = allDavx5Accounts.find { account ->
                collection.accountName == account.name && collection.accountType == account.type
            }

            // if the collection cannot be found in the list of accounts, then it was deleted, delete it also in jtx
            if (found == null) {
                Log.d("checkForDeletedAccounts", "Account ${collection.accountName} / ${collection.accountType} not found, deleting...")
                viewModelScope.launch(Dispatchers.IO) {
                    database.deleteICalCollectionbyId(collection.collectionId)
                }
            }
        }
    }

    fun delete(itemIds: List<Long>) {

        itemIds.forEach { id ->
            viewModelScope.launch(Dispatchers.IO) {
                ICalObject.deleteItemWithChildren(id, database)
            }
        }
    }

    /**
     * Inserts a new icalobject with categories
     * @param icalObject to be inserted
     * @param categories the list of categories that should be linked to the icalObject
     */
    fun insertQuickItem(icalObject: ICalObject, categories: List<Category>) {

        viewModelScope.launch(Dispatchers.IO) {
            val newId = database.insertICalObject(icalObject)

            categories.forEach {
                it.icalObjectId = newId
                database.insertCategory(it)
            }
            scrollOnceId.postValue(newId)
            quickInsertedEntity.postValue(database.getSync(newId))
        }
    }

    /**
     * This function takes an icalObjectId, retrives the icalObject and posts it  in the directEditEntity LiveData Object.
     * This can be observed and will forward the user to the edit fragment.
     * [icalObjectId] that should be opened in the edit view
     */
    fun postDirectEditEntity(icalObjectId: Long) {

        viewModelScope.launch(Dispatchers.IO) {
            directEditEntity.postValue(database.getSync(icalObjectId))
        }
    }

    /**
     * This function adds some welcome entries, this should only be used on the first install.
     * @param [context] to resolve localized string resources
     */
    fun addWelcomeEntries(context: Context) {

        val welcomeJournal = ICalObject.createJournal().apply {
            this.dtstart = DateTimeUtils.getTodayAsLong()
            this.dtstartTimezone = TZ_ALLDAY
            this.summary = context.getString(R.string.list_welcome_entry_journal_summary)
            this.description = context.getString(R.string.list_welcome_entry_journal_description)
        }

        val welcomeNote = ICalObject.createNote().apply {
            this.summary = context.getString(R.string.list_welcome_entry_note_summary)
            this.description = context.getString(R.string.list_welcome_entry_note_description)
        }

        val welcomeTodo = ICalObject.createTodo().apply {
            this.dtstart = DateTimeUtils.getTodayAsLong()
            this.dtstartTimezone = TZ_ALLDAY
            this.due = DateTimeUtils.getTodayAsLong() + 604800000  // = + one week in millis
            this.dueTimezone = TZ_ALLDAY
            this.summary = context.getString(R.string.list_welcome_entry_todo_summary)
            this.description = context.getString(R.string.list_welcome_entry_todo_description)
        }

        viewModelScope.launch(Dispatchers.IO) {
            val wj = database.insertICalObject(welcomeJournal)
            val wn = database.insertICalObject(welcomeNote)
            val wt = database.insertICalObject(welcomeTodo)

            database.insertCategory(Category().apply {
                this.icalObjectId = wj
                this.text = context.getString(R.string.list_welcome_category)
            })
            database.insertCategory(Category().apply {
                this.icalObjectId = wn
                this.text = context.getString(R.string.list_welcome_category)
            })
            database.insertCategory(Category().apply {
                this.icalObjectId = wt
                this.text = context.getString(R.string.list_welcome_category)
            })
        }
    }
}
