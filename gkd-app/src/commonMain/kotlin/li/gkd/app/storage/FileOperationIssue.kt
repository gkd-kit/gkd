package li.gkd.app.storage

import li.gkd.app.resources.*
import org.jetbrains.compose.resources.StringResource

sealed interface FileOperationIssue {
    val name: String
    val messageResource: StringResource
}

enum class ArchiveIssue(override val messageResource: StringResource) : FileOperationIssue {
    FileCountExceeded(Res.string.archive_file_count_exceeded),
    FileTooLarge(Res.string.archive_file_too_large),
    PathInvalid(Res.string.archive_path_invalid),
    PathOutsideDestination(Res.string.archive_path_outside_destination),
    TotalSizeExceeded(Res.string.archive_total_size_exceeded),
}

enum class BackupIssue(override val messageResource: StringResource) : FileOperationIssue {
    ArchiveTooLarge(Res.string.backup_archive_too_large),
    DuplicateSubscriptionId(Res.string.backup_duplicate_subscription_id),
    InvalidArchive(Res.string.backup_invalid_archive),
    SourceAccessDenied(Res.string.backup_reselect_file),
    UnknownRuleConfigType(Res.string.backup_unknown_rule_config_type),
    VersionUnsupported(Res.string.backup_version_unsupported),
    SubscriptionFileIdMismatch(Res.string.subscription_file_id_mismatch_detail),
    SubscriptionInvalidFilename(Res.string.subscription_invalid_filename),
}

enum class SnapshotIssue(override val messageResource: StringResource) : FileOperationIssue {
    FilesIncomplete(Res.string.snapshot_files_incomplete),
}
