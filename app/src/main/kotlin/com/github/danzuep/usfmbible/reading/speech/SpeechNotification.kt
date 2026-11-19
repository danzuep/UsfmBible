/*
 * Copyright (C) 2026 Daniel Collingwood
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.danzuep.usfmbible.reading.speech

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import com.github.danzuep.usfmbible.R
import com.github.danzuep.usfmbible.reading.ReadingActivity

/** Publishes TTS state to Android's media controls and notification shade while the reader is open. */
class SpeechNotification(
    context: Context,
    private val play: () -> Unit,
    private val pause: () -> Unit,
    private val stop: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NotificationManager::class.java)
    private val mediaSession = MediaSession(appContext, "USFM Bible reading").apply {
        setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        setCallback(object : MediaSession.Callback() {
            override fun onPlay() = play()
            override fun onPause() = pause()
            override fun onStop() = stop()
        })
    }

    fun update(title: String, playing: Boolean) {
        ensureChannel()
        val contentIntent = activityIntent(ACTION_OPEN, REQUEST_OPEN)
        mediaSession.setSessionActivity(contentIntent)
        mediaSession.setMetadata(
            android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, appContext.getString(R.string.app_name))
                .build()
        )
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP)
                .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, 0, if (playing) 1f else 0f)
                .build()
        )
        mediaSession.isActive = true

        val actionTitle = appContext.getString(if (playing) R.string.speech_notification_pause else R.string.speech_notification_play)
        val actionIcon = if (playing) R.drawable.ic_pause else R.drawable.ic_play
        val action = Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(appContext, actionIcon),
            actionTitle,
            activityIntent(if (playing) ACTION_PAUSE else ACTION_PLAY, if (playing) REQUEST_PAUSE else REQUEST_PLAY)
        ).build()
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(appContext, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(appContext)
        }
        val notification = builder
            .setSmallIcon(R.drawable.ic_listen)
            .setContentTitle(title)
            .setContentText(appContext.getString(if (playing) R.string.speech_notification_playing else R.string.speech_notification_paused))
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(playing)
            .addAction(action)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0)
            )
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun hide() {
        mediaSession.isActive = false
        manager.cancel(NOTIFICATION_ID)
    }

    fun release() {
        hide()
        mediaSession.release()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, appContext.getString(R.string.speech_notification_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun activityIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(appContext, ReadingActivity::class.java)
            .setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_OPEN = "com.github.danzuep.usfmbible.OPEN_SPEECH"
        const val ACTION_PLAY = "com.github.danzuep.usfmbible.PLAY_SPEECH"
        const val ACTION_PAUSE = "com.github.danzuep.usfmbible.PAUSE_SPEECH"
        private const val CHANNEL_ID = "speech"
        private const val NOTIFICATION_ID = 1
        private const val REQUEST_OPEN = 10
        private const val REQUEST_PLAY = 11
        private const val REQUEST_PAUSE = 12
    }
}
