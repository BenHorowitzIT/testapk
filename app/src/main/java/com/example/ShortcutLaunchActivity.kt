package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.engine.HomeShortcuts
import com.example.engine.SandboxManager
import kotlinx.coroutines.launch

/**
 * Invisible screen behind home-screen shortcuts: opens the sandboxed app, then closes.
 */
class ShortcutLaunchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent?.getStringExtra(HomeShortcuts.EXTRA_PACKAGE)
        if (pkg.isNullOrEmpty()) {
            finish()
            return
        }
        val sandbox = SandboxManager(applicationContext)
        lifecycleScope.launch {
            sandbox.launchByPackage(pkg).onFailure { err ->
                Toast.makeText(
                    applicationContext,
                    err.message ?: "Couldn't open the app",
                    Toast.LENGTH_LONG
                ).show()
            }
            finish()
        }
    }
}
