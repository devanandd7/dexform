package com.dexform.app

import android.content.Context
import com.dexform.app.core.ai.AiSession
import com.dexform.app.core.ai.LocalAiManager
import com.dexform.app.data.db.AppDatabase
import com.dexform.app.data.db.DocRecordDao
import com.dexform.app.data.db.FieldDefDao
import com.dexform.app.data.db.FieldValueDao
import com.dexform.app.data.db.HistoryDao
import com.dexform.app.data.db.QuickLinkDao
import com.dexform.app.data.db.SiteRuleDao
import com.dexform.app.data.db.UserProfileDao
import com.dexform.app.data.media.MediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(val context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val mediaRepository: MediaRepository by lazy {
        MediaRepository(context)
    }

    val localAiManager: LocalAiManager by lazy {
        LocalAiManager(context)
    }

    val aiSession: AiSession by lazy {
        AiSession(localAiManager, applicationScope, context)
    }

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(context)
    }

    val siteRuleDao: SiteRuleDao by lazy {
        database.siteRuleDao()
    }

    val quickLinkDao: QuickLinkDao by lazy {
        database.quickLinkDao()
    }

    val historyDao: HistoryDao by lazy {
        database.historyDao()
    }

    val userProfileDao: UserProfileDao by lazy {
        database.userProfileDao()
    }

    val fieldDefDao: FieldDefDao by lazy {
        database.fieldDefDao()
    }

    val docRecordDao: DocRecordDao by lazy {
        database.docRecordDao()
    }

    val fieldValueDao: FieldValueDao by lazy {
        database.fieldValueDao()
    }
}
