package com.example.quickconnect

import android.os.Bundle
import android.view.View
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.quickconnect.data.AppDatabase
import com.example.quickconnect.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appDatabase: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appDatabase = AppDatabase.getInstance(this)

        val navView: BottomNavigationView = binding.navView

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment

        val navController = navHostFragment.navController

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.registerFragment) {
                binding.navView.visibility = View.GONE
            } else {
                binding.navView.visibility = View.VISIBLE
            }
        }

        // Check if profileData exists
        lifecycleScope.launch {
            val profile = withContext(Dispatchers.IO) {
                appDatabase.profileDataDAO().getProfileData()
            }

            if (profile == null) {
                // No profile found, navigate to register screen
                navController.navigate(R.id.registerFragment)
            } else {
                // Optional: load main screen or default navigation
                val navView: BottomNavigationView = binding.navView
                navView.setupWithNavController(navController)
            }
        }

        navView.setupWithNavController(navController)
    }
}