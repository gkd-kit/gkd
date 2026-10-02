package li.gkd.app.ui.text

import li.gkd.app.resources.Res
import li.gkd.app.resources.archive_file_count_exceeded
import li.gkd.app.resources.archive_file_too_large
import li.gkd.app.resources.archive_name_invalid
import li.gkd.app.resources.archive_path_invalid
import li.gkd.app.resources.archive_path_outside_destination
import li.gkd.app.resources.archive_total_size_exceeded
import li.gkd.app.resources.backup_archive_too_large
import li.gkd.app.resources.backup_compress_failed
import li.gkd.app.resources.backup_duplicate_subscription_id
import li.gkd.app.resources.backup_invalid_archive
import li.gkd.app.resources.backup_read_failed
import li.gkd.app.resources.backup_reselect_file
import li.gkd.app.resources.backup_unknown_rule_config_type
import li.gkd.app.resources.backup_version_unsupported
import li.gkd.app.resources.directory_commit_failed
import li.gkd.app.resources.directory_delete_failed
import li.gkd.app.resources.directory_rollback_cleanup_failed
import li.gkd.app.resources.directory_stage_delete_failed
import li.gkd.app.resources.directory_target_exists
import li.gkd.app.resources.directory_temp_create_failed
import li.gkd.app.resources.file_too_large
import li.gkd.app.resources.screenshot_replacement_compress_failed
import li.gkd.app.resources.screenshot_restore_old_failed
import li.gkd.app.resources.screenshot_stage_old_failed
import li.gkd.app.resources.snapshot_archive_directory_cleanup_failed
import li.gkd.app.resources.snapshot_archive_directory_create_failed
import li.gkd.app.resources.snapshot_compress_failed
import li.gkd.app.resources.snapshot_directory_restore_failed
import li.gkd.app.resources.snapshot_files_incomplete
import li.gkd.app.resources.snapshot_screenshot_compress_failed
import li.gkd.app.resources.subscription_file_id_mismatch_detail
import li.gkd.app.resources.subscription_invalid_filename
import li.gkd.app.resources.unzip_directory_create_failed
import li.gkd.app.storage.StorageException
import li.gkd.app.storage.StorageIssue
import org.jetbrains.compose.resources.StringResource

fun Throwable.displayMessage(): String = when (this) {
    is li.gkd.app.subscription.SubscriptionException -> messageSync()
    is li.gkd.app.subscription.SubscriptionPersistence.DeleteException -> cause?.displayMessage() ?: toString()
    is li.gkd.app.storage.FileTooLargeException -> Res.string.file_too_large.getSync()
    is StorageException -> issue.resource.getSync(*arguments)
    else -> message ?: toString()
}

val StorageIssue.resource: StringResource
    get() = when (this) {
        StorageIssue.archive_file_count_exceeded -> Res.string.archive_file_count_exceeded
        StorageIssue.archive_file_too_large -> Res.string.archive_file_too_large
        StorageIssue.archive_name_invalid -> Res.string.archive_name_invalid
        StorageIssue.archive_path_invalid -> Res.string.archive_path_invalid
        StorageIssue.archive_path_outside_destination -> Res.string.archive_path_outside_destination
        StorageIssue.archive_total_size_exceeded -> Res.string.archive_total_size_exceeded
        StorageIssue.backup_archive_too_large -> Res.string.backup_archive_too_large
        StorageIssue.backup_compress_failed -> Res.string.backup_compress_failed
        StorageIssue.backup_duplicate_subscription_id -> Res.string.backup_duplicate_subscription_id
        StorageIssue.backup_invalid_archive -> Res.string.backup_invalid_archive
        StorageIssue.backup_read_failed -> Res.string.backup_read_failed
        StorageIssue.backup_reselect_file -> Res.string.backup_reselect_file
        StorageIssue.backup_unknown_rule_config_type -> Res.string.backup_unknown_rule_config_type
        StorageIssue.backup_version_unsupported -> Res.string.backup_version_unsupported
        StorageIssue.directory_commit_failed -> Res.string.directory_commit_failed
        StorageIssue.directory_delete_failed -> Res.string.directory_delete_failed
        StorageIssue.directory_rollback_cleanup_failed -> Res.string.directory_rollback_cleanup_failed
        StorageIssue.directory_stage_delete_failed -> Res.string.directory_stage_delete_failed
        StorageIssue.directory_target_exists -> Res.string.directory_target_exists
        StorageIssue.directory_temp_create_failed -> Res.string.directory_temp_create_failed
        StorageIssue.screenshot_replacement_compress_failed -> Res.string.screenshot_replacement_compress_failed
        StorageIssue.screenshot_restore_old_failed -> Res.string.screenshot_restore_old_failed
        StorageIssue.screenshot_stage_old_failed -> Res.string.screenshot_stage_old_failed
        StorageIssue.snapshot_archive_directory_cleanup_failed -> Res.string.snapshot_archive_directory_cleanup_failed
        StorageIssue.snapshot_archive_directory_create_failed -> Res.string.snapshot_archive_directory_create_failed
        StorageIssue.snapshot_compress_failed -> Res.string.snapshot_compress_failed
        StorageIssue.snapshot_directory_restore_failed -> Res.string.snapshot_directory_restore_failed
        StorageIssue.snapshot_files_incomplete -> Res.string.snapshot_files_incomplete
        StorageIssue.snapshot_screenshot_compress_failed -> Res.string.snapshot_screenshot_compress_failed
        StorageIssue.subscription_file_id_mismatch_detail -> Res.string.subscription_file_id_mismatch_detail
        StorageIssue.subscription_invalid_filename -> Res.string.subscription_invalid_filename
        StorageIssue.unzip_directory_create_failed -> Res.string.unzip_directory_create_failed
    }
