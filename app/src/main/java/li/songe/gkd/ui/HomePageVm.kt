package li.songe.gkd.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class HomePageVm @Inject constructor() : ViewModel() {
    val tabFlow = MutableStateFlow(controlNav)
    val intentFlow = MutableStateFlow<Intent?>(null)
}