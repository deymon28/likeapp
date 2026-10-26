package com.deymon.ytlike

import android.Manifest
import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Setup screen: permissions, Xiaomi tweaks, and a picker for the "like" action. */
class MainActivity : Activity() {
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LikeNotification.ensureChannel(this)
        val padding = dp(16)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        if (Like.hasAccess(this)) {
            NotificationListenerService.requestRebind(Like.listenerComponent(this))
        }
        render()
    }

    private fun render() {
        root.removeAllViews()
        heading("YT Like")

        val access = Like.hasAccess(this)
        text("Доступ к уведомлениям: " + if (access) "✅" else "❌ (нужен, чтобы видеть плеер)")
        if (!access) {
            button("Выдать доступ к уведомлениям") {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        if (Build.VERSION.SDK_INT >= 33) {
            val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            text("Показ уведомлений: " + if (granted) "✅" else "❌")
            if (!granted) {
                button("Разрешить уведомления") {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                }
            }
        }

        root.addView(Switch(this).apply {
            text = "Уведомление с кнопкой лайка (шторка и экран блокировки)"
            isChecked = Like.prefs(context).getBoolean(Like.KEY_SHOW_NOTIFICATION, true)
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, checked ->
                Like.prefs(context).edit().putBoolean(Like.KEY_SHOW_NOTIFICATION, checked).apply()
                MediaListenerService.refresh()
            }
        })

        text(
            "Если на заблокированном экране видно только «YT Like»: открой настройки канала " +
                "и в «На экране блокировки» выбери показ содержимого.",
            small = true,
        )
        button("Показ на экране блокировки") { openChannelSettings() }

        if (Build.VERSION.SDK_INT >= 33) {
            button("Добавить плитку «Лайк» в шторку") { requestTile() }
        }

        heading("Кнопка «громкость −»")
        val volumeEnabled = VolumeKeyService.isEnabled(this)
        text("Спецвозможность: " + if (volumeEnabled) "✅" else "❌")
        text(
            "Долгое нажатие «громкость −» (полсекунды), пока открыт YouTube Music, — действие ниже, " +
                "громкость при этом не меняется. Короткое нажатие убавляет громкость как обычно. " +
                "Работает при включённом экране, в том числе заблокированном.\n" +
                "Вибрация: одна короткая — готово, две — лайк снят, одна длинная — ошибка.",
            small = true,
        )
        if (!volumeEnabled) {
            text(
                "Если переключатель в спецвозможностях неактивен: Настройки приложения → ⋮ → " +
                    "«Разрешить ограниченные настройки», потом снова сюда.",
                small = true,
            )
            button("Включить в спецвозможностях") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        val volumeAction = Like.prefs(this)
            .getString(VolumeKeyService.KEY_VOLUME_ACTION, VolumeKeyService.ACTION_LIKE)
        root.addView(RadioGroup(this).apply {
            for ((value, label) in listOf(
                VolumeKeyService.ACTION_LIKE to "👍 Лайк",
                VolumeKeyService.ACTION_DISLIKE to "👎 Дизлайк",
                VolumeKeyService.ACTION_OFF to "Выключено",
            )) {
                addView(RadioButton(context).apply {
                    id = View.generateViewId()
                    text = label
                    isChecked = value == volumeAction
                    setOnClickListener {
                        Like.prefs(context).edit().putString(VolumeKeyService.KEY_VOLUME_ACTION, value).apply()
                    }
                })
            }
        })

        heading("Xiaomi / HyperOS")
        text(
            "Чтобы система не убивала приложение: включи автозапуск, в «Контроль активности» " +
                "выбери «Нет ограничений», в настройках уведомлений разреши показ на экране блокировки."
        )
        button("Автозапуск") { openAutostart() }
        button("Настройки приложения") { openAppDetails() }

        heading("Сейчас играет")
        val controller = if (access) Like.target(this) else null
        if (controller == null) {
            text("YouTube Music не найден. Включи трек и нажми «Обновить».")
        } else {
            text("${Like.title(controller)} — ${Like.artist(controller)}\n${controller.packageName}")
            button("👍 Лайкнуть сейчас") {
                val result = Like.like(this)
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                root.postDelayed({ render() }, 500)
            }

            val prefs = Like.prefs(this)
            val auto = Like.findCustomAction(this, controller)
            heading("Действия плеера (custom actions)")
            val actions = Like.customActions(controller)
            if (actions.isEmpty()) text("нет")
            for (action in actions) {
                val chosen = prefs.getString(Like.KEY_CUSTOM_ACTION, null) == action.action
                val mark = when {
                    chosen -> "  ← выбрано"
                    action.action == auto?.action -> "  ← авто"
                    else -> ""
                }
                text("${action.name}$mark\n${action.action}", small = true)
                button("Использовать как лайк") {
                    prefs.edit()
                        .putString(Like.KEY_CUSTOM_ACTION, action.action)
                        .remove(Like.KEY_NOTIFICATION_ACTION)
                        .apply()
                    MediaListenerService.refresh()
                    render()
                }
            }

            heading("Кнопки уведомления плеера")
            val notificationActions = Like.notificationActions(controller.packageName)
            if (notificationActions.isEmpty()) text("нет")
            for (action in notificationActions) {
                val title = action.title?.toString().orEmpty()
                val chosen = prefs.getString(Like.KEY_NOTIFICATION_ACTION, null) == title
                text(title + if (chosen) "  ← выбрано" else "", small = true)
                button("Использовать как лайк") {
                    prefs.edit()
                        .putString(Like.KEY_NOTIFICATION_ACTION, title)
                        .remove(Like.KEY_CUSTOM_ACTION)
                        .apply()
                    MediaListenerService.refresh()
                    render()
                }
            }

            button("Сбросить выбор (автоопределение)") {
                prefs.edit().remove(Like.KEY_CUSTOM_ACTION).remove(Like.KEY_NOTIFICATION_ACTION).apply()
                MediaListenerService.refresh()
                render()
            }
        }

        button("Обновить") { render() }

        heading("Диагностика")
        text(if (access) Like.describe(this) else "—", small = true)
    }

    private fun requestTile() {
        getSystemService(StatusBarManager::class.java)!!.requestAddTileService(
            ComponentName(this, LikeTileService::class.java),
            getString(R.string.tile_label),
            Icon.createWithResource(this, R.drawable.ic_thumb_up),
            mainExecutor,
        ) { }
    }

    // Apps can't set a channel's lock screen visibility themselves, only the user can.
    private fun openChannelSettings() {
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, LikeNotification.CHANNEL_ID)
        try {
            startActivity(intent)
        } catch (e: RuntimeException) {
            openAppDetails()
        }
    }

    private fun openAutostart() {
        val intent = Intent().setComponent(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            )
        )
        try {
            startActivity(intent)
        } catch (e: RuntimeException) {
            openAppDetails()
        }
    }

    private fun openAppDetails() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        )
    }

    private fun heading(value: String) {
        root.addView(TextView(this).apply {
            text = value
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(20), 0, dp(6))
        })
    }

    private fun text(value: String, small: Boolean = false) {
        root.addView(TextView(this).apply {
            text = value
            textSize = if (small) 13f else 15f
            setTextIsSelectable(true)
            setPadding(0, dp(4), 0, dp(4))
        })
    }

    private fun button(label: String, onClick: (View) -> Unit) {
        root.addView(Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener(onClick)
        })
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
