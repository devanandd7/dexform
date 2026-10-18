package com.dexform.app

import android.content.Context
import com.dexform.app.core.ai.LocalAiManager
import com.dexform.app.data.db.AppDatabase
import com.dexform.app.data.db.HistoryDao
import com.dexform.app.data.db.QuickLinkDao
import com.dexform.app.data.db.SiteRuleDao
import com.dexform.app.data.media.MediaRepository

class AppContainer(val context: Context) {
    val mediaRepository: MediaRepository by lazy {
        MediaRepository(context)
    }

    val localAiManager: LocalAiManager by lazy {
        LocalAiManager(context)
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
}
