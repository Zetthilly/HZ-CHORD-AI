package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.HZChordAiApp
import com.example.viewmodel.WorkstationViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val workstationViewModel: WorkstationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HZChordAiApp(workstationViewModel) }
    }

    override fun onPause() {
        super.onPause()
        workstationViewModel.saveCurrentModuleStateImmediately()
    }
}
