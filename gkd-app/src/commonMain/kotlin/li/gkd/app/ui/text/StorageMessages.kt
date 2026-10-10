package li.gkd.app.ui.text

import li.gkd.app.resources.*
import li.gkd.app.storage.ArchiveIssue
import li.gkd.app.storage.BackupIssue
import li.gkd.app.storage.SnapshotIssue
import li.gkd.app.storage.StorageException
import li.gkd.app.storage.FileOperationIssue
import org.jetbrains.compose.resources.StringResource

fun Throwable.displayMessage(): String = when (this) {
    is li.gkd.app.subscription.SubscriptionException -> messageSync()
    is li.gkd.app.subscription.SubscriptionPersistence.DeleteException -> cause?.displayMessage() ?: toString()
    is li.gkd.app.storage.FileTooLargeException -> Res.string.file_too_large.getSync()
    is StorageException -> issue.resource.getSync(*arguments)
    else -> message ?: toString()
}

val FileOperationIssue.resource: StringResource
    get() = when (this) {
        is ArchiveIssue -> when (this) {
            ArchiveIssue.FileCountExceeded -> Res.string.archive_file_count_exceeded
            ArchiveIssue.FileTooLarge -> Res.string.archive_file_too_large
            ArchiveIssue.PathInvalid -> Res.string.archive_path_invalid
            ArchiveIssue.PathOutsideDestination -> Res.string.archive_path_outside_destination
            ArchiveIssue.TotalSizeExceeded -> Res.string.archive_total_size_exceeded
        }
        is BackupIssue -> when (this) {
            BackupIssue.ArchiveTooLarge -> Res.string.backup_archive_too_large
            BackupIssue.DuplicateSubscriptionId -> Res.string.backup_duplicate_subscription_id
            BackupIssue.InvalidArchive -> Res.string.backup_invalid_archive
            BackupIssue.SourceAccessDenied -> Res.string.backup_reselect_file
            BackupIssue.UnknownRuleConfigType -> Res.string.backup_unknown_rule_config_type
            BackupIssue.VersionUnsupported -> Res.string.backup_version_unsupported
            BackupIssue.SubscriptionFileIdMismatch -> Res.string.subscription_file_id_mismatch_detail
            BackupIssue.SubscriptionInvalidFilename -> Res.string.subscription_invalid_filename
        }
        is SnapshotIssue -> when (this) {
            SnapshotIssue.FilesIncomplete -> Res.string.snapshot_files_incomplete
        }
    }
