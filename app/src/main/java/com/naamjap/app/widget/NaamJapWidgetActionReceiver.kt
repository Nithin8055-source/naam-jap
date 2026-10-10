package com.naamjap.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.naamjap.app.MainActivity

class NaamJapWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INCREMENT) return
        if (NaamJapWidgetSnapshotStore.shouldOpenAppForAction(context)) {
            val status = NaamJapWidgetSnapshotStore.read(context)?.status
            val openJap = NaamJapWidgetSnapshotStore.actionShouldOpenJap(context) ||
                status == "Open Jap to start" || status == "Open Jap to resume" || status == "Open app to count"
            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (openJap) putExtra(EXTRA_WIDGET_OPEN_JAP, true)
            }
            context.startActivity(open)
            return
        }
        val snapshot = NaamJapWidgetSnapshotStore.read(context) ?: return
        // This is only a queued status; the number remains the last server-confirmed total.
        NaamJapWidgetSnapshotStore.setStatusForSnapshot(context, snapshot, "Count queued - syncing")
        NaamJapWidgetRenderer.updateAll(context)
        NaamJapWidgetWork.enqueueIncrement(
            context,
            accountKey = snapshot.accountKey,
            sessionId = requireNotNull(snapshot.activeSessionId)
        )
    }

    companion object {
        const val ACTION_INCREMENT = "com.naamjap.app.widget.ACTION_INCREMENT"
    }
}
