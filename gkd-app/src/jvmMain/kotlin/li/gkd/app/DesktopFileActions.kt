package li.gkd.app

import com.sun.jna.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.gkd.app.storage.FileExports
import java.awt.Window
import java.io.File
import javax.swing.JFileChooser

/** Native file selection is the only host-specific part of archive import/export. */
class DesktopFileActions(private val fileDialogOwner: () -> Window) {
    suspend fun choose(directory: File, save: File? = null): File? = withContext(Dispatchers.Main) {
        val owner = fileDialogOwner()
        check(owner.isDisplayable) { "File dialog owner is closed" }
        if (Platform.isWindows()) return@withContext WindowsFileDialog.choose(owner, directory, save)
        val chooser = JFileChooser(directory).apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            if (save != null) selectedFile = save
        }
        val result =
            if (save == null) chooser.showOpenDialog(owner) else chooser.showSaveDialog(owner)
        if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    suspend fun saveAs(source: File): Boolean {
        val target = choose(source.parentFile, source) ?: return false
        FileExports.copyTo(source, target)
        return true
    }
}
