package li.songe.gkd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import li.songe.gkd.ui.component.UploadOptions

class AboutVm : ViewModel() {
    val uploadOptions = UploadOptions(viewModelScope)
}