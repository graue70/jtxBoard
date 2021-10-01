/*
 * Copyright (c) Patrick Lang in collaboration with bitfire web engineering.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.techbee.jtx

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.preference.PreferenceManager
import at.techbee.jtx.database.ICalObject
import at.techbee.jtx.database.relations.ICalEntity
import at.techbee.jtx.ui.IcalListFragmentDirections
import at.techbee.jtx.ui.SettingsFragment
import com.google.android.gms.ads.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.android.gms.ads.rewarded.RewardItem
import net.fortuna.ical4j.util.MapTimeZoneCache


// this is necessary for the app permission, 100  and 200 ist just a freely chosen value
const val CONTACT_READ_PERMISSION_CODE = 100
const val RECORD_AUDIO_PERMISSION_CODE = 200

const val AUTHORITY_FILEPROVIDER = "at.techbee.jtx.fileprovider"

/**
 * This main activity is just a container for our fragments,
 * where the real action is.
 */
class MainActivity : AppCompatActivity(), OnUserEarnedRewardListener  {

    companion object {
        const val CHANNEL_REMINDER_DUE = "REMINDER_DUE"
        //const val TRIAL_PERIOD_DAYS = 14L


    }

    private lateinit var toolbar: Toolbar

    private var settings: SharedPreferences? = null


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //load settings
        settings = PreferenceManager.getDefaultSharedPreferences(this)


        // Register Notification Channel for Reminders
        createNotificationChannel()

        // Set up the toolbar with the navigation drawer
        toolbar = findViewById(R.id.topAppBar)
        setToolbarText("Board")

        setSupportActionBar(toolbar)

        // necessary for ical4j
        System.setProperty("net.fortuna.ical4j.timezone.cache.impl", MapTimeZoneCache::class.java.name)


        setUpDrawer()
        checkThemeSetting()

        // if trial period ended, then check if ads are accepted, if the
        // app was bought, then skip the Dialog, otherwise show the dialog to let the user choose
        val adsAccepted = settings!!.getBoolean(SettingsFragment.ACCEPT_ADS, false)
        if (!AdLoader.isTrialPeriod(this) && !adsAccepted) {

            // TODO: Check if the user already bought the app. If yes, skip the Dialog Box

            //MaterialAlertDialogBuilder(applicationContext, R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog_Centered)
            MaterialAlertDialogBuilder(this)
                .setTitle(resources.getString(R.string.list_dialog_contribution_title))
                .setMessage(resources.getString(R.string.list_dialog_contribution_message))
                .setNegativeButton(resources.getString(R.string.list_dialog_contribution_buyadfree)) { _, _ ->
                    // Respond to negative button press
                    settings!!.edit().putBoolean(SettingsFragment.ACCEPT_ADS, false).apply()
                    Toast.makeText(this, "Start the Intent for the play store", Toast.LENGTH_LONG).show()
                    //TODO: open in app-buying for ad-free option
                }
                .setPositiveButton(resources.getString(R.string.list_dialog_contribution_acceptads)) { _, _ ->
                    // Respond to positive button press
                    // Ads are accepted, load user consent
                    settings!!.edit().putBoolean(SettingsFragment.ACCEPT_ADS, true).apply()
                    AdLoader.initializeUserConsent(this, applicationContext)
                }
                .show()
        }
        else if (!AdLoader.isTrialPeriod(this) && adsAccepted) {
            AdLoader.initializeUserConsent(this, applicationContext)
            Log.d("Ads accepted", "Ads accepted, loading consent form if necessary")
        }

    }


    override fun onResume() {
        super.onResume()

        // handle the intents for the shortcuts
        when (intent.action) {
            "addJournal" -> {
                findNavController(R.id.nav_host_fragment)
                    .navigate(
                        IcalListFragmentDirections.actionIcalListFragmentToIcalEditFragment(
                            ICalEntity(ICalObject.createJournal())
                        )
                    )
            }
            "addNote" -> {
                findNavController(R.id.nav_host_fragment)
                    .navigate(
                        IcalListFragmentDirections.actionIcalListFragmentToIcalEditFragment(
                            ICalEntity(ICalObject.createNote())
                        )
                    )
            }
            "addTodo" -> {
                findNavController(R.id.nav_host_fragment)
                    .navigate(
                        IcalListFragmentDirections.actionIcalListFragmentToIcalEditFragment(
                            ICalEntity(ICalObject.createTodo())
                        )
                    )
            }
        }

    }


    private fun setUpDrawer() {

        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_main_layout)
        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()


        // React on selection in Navigation View
        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener { menuItem ->

            //close drawer
            drawerLayout.close()

            // Handle menu item selected
            when (menuItem.itemId) {

                R.id.nav_about ->
                    findNavController(R.id.nav_host_fragment)
                        .navigate(R.id.action_global_aboutFragment)

                R.id.nav_app_settings ->
                    findNavController(R.id.nav_host_fragment)
                        .navigate(R.id.action_global_settingsFragment)

                R.id.nav_twitter ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://techbee.at")))

                R.id.nav_website ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://jtx.techbee.at")))

                R.id.nav_manual ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://jtx.techbee.at")))

                R.id.nav_faq ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://jtx.techbee.at")))

                R.id.nav_forums ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bitfire.at")))

                R.id.nav_donate ->
                    //if (BuildConfig.FLAVOR != App.FLAVOR_GOOGLE_PLAY)
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bitfire.at")))

                R.id.nav_privacy ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bitfire.at")))
            }
            true
        }
    }

    private fun checkThemeSetting() {
        // user interface settings
        val enforceDark = settings!!.getBoolean(SettingsFragment.ENFORCE_DARK_THEME, false)
        if (enforceDark)
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        else
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }



    // this is called when the user accepts a permission
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        if (requestCode == CONTACT_READ_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this, "Contacts Read Permission Granted", Toast.LENGTH_SHORT).show()
            else
                Toast.makeText(this, "Contacts Read Permission Denied", Toast.LENGTH_SHORT).show()
        } else if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this, "Record Audio Permission Granted", Toast.LENGTH_SHORT).show()
            else
                Toast.makeText(this, "Record Audio Permission Denied", Toast.LENGTH_SHORT).show()
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_reminder_name)
            val descriptionText = getString(R.string.notification_channel_reminder_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_REMINDER_DUE, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }


    fun setToolbarText(text: String) {
        toolbar.title = text
    }


    override fun onUserEarnedReward(item: RewardItem) {
        Log.d("onUserEarnedReward", "Ad watched, user earned Reward")
    }
}


