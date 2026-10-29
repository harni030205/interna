package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserEntity::class,
        RolePermissionEntity::class,
        UserPreferenceEntity::class,
        SubscriptionEntity::class,
        SubscriptionRequestEntity::class,
        PaymentMethodConfigEntity::class,
        UsageRecordEntity::class,
        ConversationSessionEntity::class,
        ConversationMessageEntity::class,
        LearningProgressEntity::class,
        SavedVocabularyEntity::class,
        DictionaryCacheEntity::class,
        OfflineLessonEntity::class,
        SurveyEntity::class,
        SurveyResponseEntity::class,
        FeedbackEntity::class,
        NotificationEntity::class,
        AdminAuditLogEntity::class,
        ApplicationSettingsEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class InternaDatabase : RoomDatabase() {
    abstract fun dao(): InternaDao

    companion object {
        @Volatile
        private var INSTANCE: InternaDatabase? = null

        /**
         * Non-destructive migration from v1 to v2 that preserves all existing user accounts,
         * vocabulary, progress, conversations, and settings while adding mobile-money payment
         * support and one-time administrator provisioning lock columns.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `subscription_requests` ADD COLUMN `paymentMethod` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `subscription_requests` ADD COLUMN `transactionReference` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `subscription_requests` ADD COLUMN `normalizedTxRef` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `subscription_requests` ADD COLUMN `amountPaid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `subscription_requests` ADD COLUMN `currency` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `subscription_requests` ADD COLUMN `paymentDate` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `subscription_requests` ADD COLUMN `proofOfPaymentUri` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `subscription_requests` ADD COLUMN `verificationMode` TEXT NOT NULL DEFAULT 'MANUAL_VERIFICATION'")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subscription_requests_normalizedTxRef` ON `subscription_requests` (`normalizedTxRef`)")

                db.execSQL("ALTER TABLE `application_settings` ADD COLUMN `adminProvisionTokenConsumed` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `application_settings` ADD COLUMN `adminProvisionedUserId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `application_settings` ADD COLUMN `adminProvisionedAt` INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `payment_method_configs` (
                        `methodCode` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `receivingAccountNumber` TEXT NOT NULL,
                        `receivingAccountName` TEXT NOT NULL,
                        `currency` TEXT NOT NULL,
                        `monthlyPrice` TEXT NOT NULL,
                        `quarterlyPrice` TEXT NOT NULL,
                        `yearlyPrice` TEXT NOT NULL,
                        `transferInstructions` TEXT NOT NULL,
                        `updatedByAdminId` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`methodCode`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): InternaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    InternaDatabase::class.java,
                    "interna_platform.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
