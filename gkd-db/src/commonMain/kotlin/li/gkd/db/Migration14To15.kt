package li.gkd.db

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object Migration14To15 : Migration(14, 15) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // Keep the whole record with the smallest legacy id for each business key.
        // Orphaned overrides from deleted subscriptions have no owner in the new schema.
        connection.execSQL(
            """
            CREATE TABLE app_group_config (
                subs_id INTEGER NOT NULL,
                app_id TEXT NOT NULL,
                group_key INTEGER NOT NULL,
                enable INTEGER,
                exclude TEXT NOT NULL DEFAULT '',
                PRIMARY KEY (subs_id, app_id, group_key),
                FOREIGN KEY (subs_id) REFERENCES subs_item(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """
                .trimIndent(),
        )
        connection.execSQL(
            """
            INSERT INTO app_group_config (subs_id, app_id, group_key, enable, exclude)
            SELECT subs_id, app_id, group_key, enable, exclude FROM subs_config
            WHERE id IN (
                SELECT MIN(id) FROM subs_config
                WHERE type = 2 AND subs_id IN (SELECT id FROM subs_item)
                GROUP BY subs_id, app_id, group_key
            )
            """
                .trimIndent(),
        )
        connection.execSQL(
            """
            CREATE TABLE global_group_config (
                subs_id INTEGER NOT NULL,
                group_key INTEGER NOT NULL,
                enable INTEGER,
                exclude TEXT NOT NULL DEFAULT '',
                PRIMARY KEY (subs_id, group_key),
                FOREIGN KEY (subs_id) REFERENCES subs_item(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """
                .trimIndent(),
        )
        connection.execSQL(
            """
            INSERT INTO global_group_config (subs_id, group_key, enable, exclude)
            SELECT subs_id, group_key, enable, exclude FROM subs_config
            WHERE id IN (
                SELECT MIN(id) FROM subs_config
                WHERE type = 3 AND subs_id IN (SELECT id FROM subs_item)
                GROUP BY subs_id, group_key
            )
            """
                .trimIndent(),
        )
        connection.execSQL(
            """
            CREATE TABLE app_config_new (
                subs_id INTEGER NOT NULL,
                app_id TEXT NOT NULL,
                enable INTEGER NOT NULL,
                PRIMARY KEY (subs_id, app_id),
                FOREIGN KEY (subs_id) REFERENCES subs_item(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """
                .trimIndent(),
        )
        connection.execSQL(
            """
            INSERT INTO app_config_new (subs_id, app_id, enable)
            SELECT subs_id, app_id, enable FROM app_config
            WHERE id IN (
                SELECT MIN(id) FROM app_config
                WHERE 1 AND subs_id IN (SELECT id FROM subs_item)
                GROUP BY subs_id, app_id
            )
            """
                .trimIndent(),
        )
        connection.execSQL("DROP TABLE app_config")
        connection.execSQL("ALTER TABLE app_config_new RENAME TO app_config")
        connection.execSQL(
            """
            CREATE TABLE category_config_new (
                subs_id INTEGER NOT NULL,
                category_key INTEGER NOT NULL,
                enable INTEGER,
                PRIMARY KEY (subs_id, category_key),
                FOREIGN KEY (subs_id) REFERENCES subs_item(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """
                .trimIndent(),
        )
        connection.execSQL(
            """
            INSERT INTO category_config_new (subs_id, category_key, enable)
            SELECT subs_id, category_key, enable FROM category_config
            WHERE id IN (
                SELECT MIN(id) FROM category_config
                WHERE 1 AND subs_id IN (SELECT id FROM subs_item)
                GROUP BY subs_id, category_key
            )
            """
                .trimIndent(),
        )
        connection.execSQL("DROP TABLE category_config")
        connection.execSQL("ALTER TABLE category_config_new RENAME TO category_config")
        connection.execSQL("DROP TABLE subs_config")
    }
}
