package li.gkd.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.gkd.app.storage.FileExports
import java.io.File
import javax.swing.JFileChooser

/** Native file selection is the only host-specific part of archive import/export. */
object DesktopFileActions {
    suspend fun choose(directory: File, save: File? = null): File? = withContext(Dispatchers.Main) {
        val chooser = JFileChooser(directory).apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            if (save != null) selectedFile = save
        }
        val result =
            if (save == null) chooser.showOpenDialog(null) else chooser.showSaveDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    suspend fun saveAs(source: File): Boolean {
        val target = choose(source.parentFile, source) ?: return false
        FileExports.copyTo(source, target)
        return true
    }
}
