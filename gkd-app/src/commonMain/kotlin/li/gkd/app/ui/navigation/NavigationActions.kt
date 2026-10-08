package li.gkd.app.ui.navigation

import li.gkd.app.ui.share.DeletionTarget

typealias ConfirmDeletion = (String, String, () -> Set<DeletionTarget>, () -> Unit, suspend () -> Unit) -> Unit
