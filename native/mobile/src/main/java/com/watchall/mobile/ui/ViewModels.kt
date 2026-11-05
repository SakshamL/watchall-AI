package com.watchall.mobile.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@Composable
inline fun <reified T : ViewModel> watchAllViewModel(
    key: String? = null,
    noinline create: () -> T,
): T = viewModel(
    key = key,
    factory = viewModelFactory { initializer { create() } },
)
