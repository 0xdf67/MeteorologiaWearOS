package com.thewizrd.simpleweather.preferences

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.thewizrd.simpleweather.helpers.WindowColorManager
import kotlinx.coroutines.launch

abstract class WindowColorPreferenceFragmentCompat : CustomPreferenceFragmentCompat(),
    WindowColorManager {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                updateWindowColors()
            }
        }
    }

    override fun updateWindowColors() {}
}