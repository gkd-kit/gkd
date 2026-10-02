package li.gkd.app.storage

import java.io.IOException

class StorageException(
    val issue: StorageIssue,
    vararg val arguments: Any?,
    cause: Throwable? = null,
) : IOException(issue.name + arguments.joinToString(prefix = ": "), cause)

enum class StorageIssue {
    archive_file_count_exceeded,
    archive_file_too_large,
    archive_name_invalid,
    archive_path_invalid,
    archive_path_outside_destination,
    archive_total_size_exceeded,
    backup_archive_too_large,
    backup_compress_failed,
    backup_duplicate_subscription_id,
    backup_invalid_archive,
    backup_read_failed,
    backup_reselect_file,
    backup_unknown_rule_config_type,
    backup_version_unsupported,
    directory_commit_failed,
    directory_delete_failed,
    directory_rollback_cleanup_failed,
    directory_stage_delete_failed,
    directory_target_exists,
    directory_temp_create_failed,
    screenshot_replacement_compress_failed,
    screenshot_restore_old_failed,
    screenshot_stage_old_failed,
    snapshot_archive_directory_cleanup_failed,
    snapshot_archive_directory_create_failed,
    snapshot_compress_failed,
    snapshot_directory_restore_failed,
    snapshot_files_incomplete,
    snapshot_screenshot_compress_failed,
    subscription_file_id_mismatch_detail,
    subscription_invalid_filename,
    unzip_directory_create_failed,
}
