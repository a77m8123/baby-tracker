package com.babytracker.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.babytracker.notifications.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(EXTRA_KIND) ?: return
        val id = intent.getIntExtra(EXTRA_ID, 0)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (kind) {
                    "FEED" -> Notifications.showFeedDue(context)
                    "WAKE" -> Notifications.showWakeUp(context)
                    else -> Unit
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_KIND = "kind"
        const val EXTRA_ID = "id"
    }
}
