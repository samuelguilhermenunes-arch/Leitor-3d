package com.modscout

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Environment
import android.os.FileObserver
import android.os.IBinder
import java.io.File

/**
 * Roda em segundo plano (com notificação fixa) e registra quais arquivos
 * da pasta do jogo são abertos, criados, alterados ou apagados.
 */
class WatchService : Service() {

    private val observers = HashMap<String, FileObserver>()
    private var pkg: String = ""
    private var poller: Thread? = null
    @Volatile private var running = false
    @Volatile private var gameWasForeground = false
    private var saf: SafWatcher? = null

    private val mask = FileObserver.OPEN or FileObserver.CREATE or FileObserver.MODIFY or
        FileObserver.DELETE or FileObserver.MOVED_TO or FileObserver.MOVED_FROM

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        pkg = intent?.getStringExtra(EXTRA_PKG) ?: ""
        val tree = intent?.getStringExtra(EXTRA_TREE)
        if (pkg.isEmpty() && tree == null) return START_NOT_STICKY
        saf = tree?.let { SafWatcher(this, Uri.parse(it)) { k, p -> LogStore.add(k, p) } }
        startAsForeground()
        startWatching()
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Observando o jogo", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = android.app.PendingIntent.getService(
            this, 0,
            Intent(this, WatchService::class.java).setAction(ACTION_STOP),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("ModScout está observando")
            .setContentText(pkg)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Parar", stop).build())
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    private fun startWatching() {
        running = true
        LogStore.add("INFO", "Observação iniciada para $pkg")

        val root = Environment.getExternalStorageDirectory()
        val dirs = listOf(
            File(root, "Android/data/$pkg"),
            File(root, "Android/obb/$pkg")
        )
        var watched = if (saf != null) 1 else 0
        for (d in dirs.filter { pkg.isNotEmpty() }) {
            if (d.isDirectory && d.listFiles() != null) {
                watchTree(d, root)
                watched++
                LogStore.add("INFO", "Observando ${d.relativeTo(root).path}")
            } else {
                LogStore.add(
                    "INFO",
                    "Sem acesso a ${d.relativeTo(root).path} (Android 11+ bloqueia esta pasta para apps comuns)"
                )
            }
        }
        if (watched == 0) {
            LogStore.add("INFO", "Nenhuma pasta acessível. Veja a aba Ajuda.")
        }

        poller = Thread {
            try {
                while (running) {
                    if (pkg.isNotEmpty()) checkForeground()
                    saf?.tick()
                    Thread.sleep(2000)
                }
            } catch (_: InterruptedException) {
            }
        }.also { it.start() }
    }

    private fun watchTree(dir: File, root: File) {
        if (observers.containsKey(dir.path)) return
        val obs = object : FileObserver(dir, mask) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                val full = File(dir, path)
                val rel = full.relativeTo(root).path
                val e = event and ALL_EVENTS
                when {
                    e and CREATE != 0 -> {
                        LogStore.add("CRIOU", rel)
                        if (full.isDirectory) watchTree(full, root)
                    }
                    e and MOVED_TO != 0 -> {
                        LogStore.add("MOVEU PARA", rel)
                        if (full.isDirectory) watchTree(full, root)
                    }
                    e and MODIFY != 0 -> LogStore.add("ALTEROU", rel)
                    e and DELETE != 0 -> LogStore.add("APAGOU", rel)
                    e and MOVED_FROM != 0 -> LogStore.add("MOVEU DE", rel)
                    e and OPEN != 0 -> if (!full.isDirectory) LogStore.add("ABRIU", rel)
                }
            }
        }
        obs.startWatching()
        observers[dir.path] = obs
        dir.listFiles()?.filter { it.isDirectory }?.forEach { watchTree(it, root) }
    }

    private fun checkForeground() {
        val usm = getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 5000, now)
        val e = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last = e.packageName
        }
        if (last == pkg && !gameWasForeground) {
            gameWasForeground = true
            LogStore.add("INFO", "Jogo aberto na tela")
        } else if (last != null && last != pkg) {
            gameWasForeground = false
        }
    }

    override fun onDestroy() {
        running = false
        poller?.interrupt()
        observers.values.forEach { it.stopWatching() }
        observers.clear()
        LogStore.add("INFO", "Observação parada")
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PKG = "pkg"
        const val EXTRA_TREE = "tree"
        const val ACTION_STOP = "com.modscout.STOP"
        private const val CHANNEL = "watch"
    }
}
