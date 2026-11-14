package com.akaaflix.launcher.device.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

data class NotificationItem(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val contentIntent: PendingIntent?,
    val autoCancel: Boolean,
    val clearable: Boolean,
)

/** In-memory notification list fed by [LauncherNotificationListener] once the user grants access. */
object NotificationCenter {
    private val _items = MutableStateFlow<List<NotificationItem>>(emptyList())
    val items: StateFlow<List<NotificationItem>> = _items.asStateFlow()
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()
    internal var service: WeakReference<LauncherNotificationListener>? = null

    fun isAccessGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    internal fun publish(list: Array<StatusBarNotification>?) {
        _items.value = list.orEmpty().mapNotNull { map(it) }.sortedByDescending { it.postTime }
    }

    internal fun setConnected(value: Boolean) {
        _connected.value = value
        if (!value) _items.value = emptyList()
    }

    fun dismiss(key: String) {
        try { service?.get()?.cancelNotification(key) } catch (_: Exception) {}
        _items.value = _items.value.filterNot { it.key == key }
    }

    fun dismissAll() {
        try { service?.get()?.cancelAllNotifications() } catch (_: Exception) {}
        _items.value = _items.value.filterNot { it.clearable }
    }

    fun open(item: NotificationItem): Boolean {
        val pi = item.contentIntent ?: return false
        return try {
            pi.send()
            if (item.autoCancel) dismiss(item.key)
            true
        } catch (_: Exception) { false }
    }

    private fun map(sbn: StatusBarNotification): NotificationItem? {
        val n = sbn.notification ?: return null
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return null
        if (sbn.isOngoing && n.category == Notification.CATEGORY_SERVICE) return null
        val extras = n.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras?.getCharSequence(Notification.EXTRA_TEXT) ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        return NotificationItem(sbn.key, sbn.packageName, title, text, sbn.postTime, n.contentIntent,
            (n.flags and Notification.FLAG_AUTO_CANCEL) != 0, sbn.isClearable)
    }
}

/**
 * Optional notification listener (user must enable it in Android settings). Only used to show
 * notifications in the launcher's bell panel; nothing is stored or sent anywhere.
 */
class LauncherNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        NotificationCenter.service = WeakReference(this)
        NotificationCenter.setConnected(true)
        refresh()
    }

    override fun onListenerDisconnected() {
        NotificationCenter.setConnected(false)
        NotificationCenter.service = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        try { NotificationCenter.publish(activeNotifications) } catch (_: Exception) {}
    }
}
