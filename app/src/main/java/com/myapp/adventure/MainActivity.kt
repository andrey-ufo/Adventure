package com.myapp.adventure

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.myapp.adventure.ui.setup.SetupFragment
import com.myapp.adventure.utils.Logger
import com.myapp.adventure.utils.ThemeManager
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Theme bootstrap must happen BEFORE super.onCreate(): AppCompat reads
        // the night mode and theme during its own onCreate, and changing them
        // afterwards causes a flash or a spurious recreate().
        ThemeManager.initialize(this)
        runBlocking { ThemeManager.awaitInitialized() }
        ThemeManager.apply(this)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("adventure_prefs", MODE_PRIVATE)
        val savedUri = prefs.getString("adventure_folder_uri", null)
        if (savedUri != null) {
            Logger.init(this, savedUri)
        }

        com.myapp.adventure.step.StepSourceManager.start(this)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SetupFragment())
                .commit()
        }
    }
}