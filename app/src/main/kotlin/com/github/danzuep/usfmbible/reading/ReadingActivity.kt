/*
 * Copyright (C) 2023 Xizhi Zhu
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

package com.github.danzuep.usfmbible.reading

import android.Manifest
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ActionMode
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import java.util.Locale
import co.touchlab.kermit.Logger
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.github.danzuep.usfmbible.Navigator
import com.github.danzuep.usfmbible.R
import com.github.danzuep.usfmbible.core.Bible
import com.github.danzuep.usfmbible.core.Highlight
import com.github.danzuep.usfmbible.core.Settings
import com.github.danzuep.usfmbible.core.Verse
import com.github.danzuep.usfmbible.core.VerseIndex
import com.github.danzuep.usfmbible.databinding.ActivityReadingBinding
import com.github.danzuep.usfmbible.infra.BaseActivity
import com.github.danzuep.usfmbible.reading.chapter.ChapterSelectionFragment
import com.github.danzuep.usfmbible.search.SearchActivity
import com.github.danzuep.usfmbible.reading.detail.VerseDetailViewLayout
import com.github.danzuep.usfmbible.reading.detail.VerseTextItem
import com.github.danzuep.usfmbible.reading.toolbar.ReadingToolbarFragment
import com.github.danzuep.usfmbible.reading.speech.ChapterSpeaker
import com.github.danzuep.usfmbible.reading.speech.SpeechNotification
import com.github.danzuep.usfmbible.reading.speech.SpeechVerseTimer
import com.github.danzuep.usfmbible.reading.speech.adjacentSpeechChapter
import com.github.danzuep.usfmbible.reading.speech.chapterSpeechFraction
import com.github.danzuep.usfmbible.reading.speech.speechPlaybackSpeeds
import com.github.danzuep.usfmbible.reading.verse.ParagraphItem
import com.github.danzuep.usfmbible.reading.verse.VersePagerAdapter
import com.github.danzuep.usfmbible.reading.verse.toBookIndex
import com.github.danzuep.usfmbible.reading.verse.toChapterIndex
import com.github.danzuep.usfmbible.reading.verse.toPagePosition
import com.github.danzuep.usfmbible.reading.verse.toStringForCopy
import com.github.danzuep.usfmbible.translations.TranslationsActivity
import com.github.danzuep.usfmbible.ui.highlightSwatch
import com.github.danzuep.usfmbible.ui.dialog
import com.github.danzuep.usfmbible.ui.indeterminateProgressDialog
import com.github.danzuep.usfmbible.ui.listDialog
import com.github.danzuep.usfmbible.ui.languageDisplayNameWithNative
import com.github.danzuep.usfmbible.ui.makeLessSensitive
import com.github.danzuep.usfmbible.ui.toast
import com.github.danzuep.usfmbible.utils.copyToClipBoard
import com.github.danzuep.usfmbible.utils.shareToSystem

@AndroidEntryPoint
class ReadingActivity : BaseActivity<ActivityReadingBinding, ReadingViewModel.ViewAction, ReadingViewModel.ViewState, ReadingViewModel>(),
    ParagraphItem.Callback, VerseTextItem.Callback {
    companion object {
        private const val KEY_OPEN_NOTE = "com.github.danzuep.usfmbible.KEY_OPEN_NOTE"

        /** Optional launch extra: the 0-based index of the book to open (e.g. 42 for John) instead of the last reading position. */
        const val KEY_SELECT_VERSE = "com.github.danzuep.usfmbible.KEY_SELECT_VERSE"
        private const val KEY_SPEECH_VERSE = "com.github.danzuep.usfmbible.KEY_SPEECH_VERSE"
        const val KEY_OPEN_BOOK = "com.github.danzuep.usfmbible.KEY_OPEN_BOOK"

        fun bundleForOpenNote(): Bundle = Bundle().apply { putBoolean(KEY_OPEN_NOTE, true) }
    }

    override val viewModel: ReadingViewModel by viewModels()

    override val viewBinding: ActivityReadingBinding by lazy(LazyThreadSafetyMode.NONE) { ActivityReadingBinding.inflate(layoutInflater) }

    private val selectedVerses: MutableSet<Verse> = mutableSetOf()
    private var settings: Settings? = null
    private var currentReadingStatus: ReadingViewModel.ViewState.CurrentReadingStatus? = null

    private var actionMode: ActionMode? = null
    private enum class Speech { OFF, PLAYING, PAUSED }

    private var speech = Speech.OFF
    // The verse being read, or the one to resume from while paused.
    private var spokenVerse = VerseIndex.INVALID
    private var spokenVerses = emptyList<VerseIndex>()
    private var spokenHighlight: VerseIndex? = null
    private var speechRequest = 0
    private var speechWordsByVerse = emptyMap<VerseIndex, Int>()
    private var speechTotalWords = 0
    private val speechVerseTimer = SpeechVerseTimer()
    private var speechPausedFraction = 0f
    private var speechMillisecondsPerWord = 400f
    private var speechRateInitialized = false
    private val speechChapterDuration: Long
        get() = if (speechTotalWords == 0) 0L else (speechTotalWords * speechMillisecondsPerWord).toLong().coerceAtLeast(1L)
    private var speechTimeMenuItem: MenuItem? = null
    private var lastMediaProgressUpdate = 0L
    private var speechRevealRequest = 0
    private var speechRevealPending = false
    private var notificationPermissionRequested = false
    private val speechProgressHandler = Handler(Looper.getMainLooper())
    private val speechProgressTick = object : Runnable {
        override fun run() {
            updateSpeechProgress()
            if (speech == Speech.PLAYING) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastMediaProgressUpdate >= 1000 && speechNotificationDelegate.isInitialized()) {
                    speechNotification.updatePosition(true, speechPosition())
                    lastMediaProgressUpdate = now
                }
                speechProgressHandler.postDelayed(this, 100)
            }
        }
    }
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) updateSpeechNotification()
    }
    private val speechNotificationDelegate = lazy(LazyThreadSafetyMode.NONE) {
        SpeechNotification(this, ::resumeSpeech, ::pauseSpeech, ::stopSpeech, { skipSpeechChapter(-1) }, { skipSpeechChapter(1) })
    }
    private val speechNotification by speechNotificationDelegate
    private val speakerDelegate = lazy(LazyThreadSafetyMode.NONE) {
        ChapterSpeaker(this, object : ChapterSpeaker.Listener {
            override fun onVerseQueued(verseIndex: VerseIndex) = followSpokenVerse(verseIndex, false)
            override fun onVerseStarted(verseIndex: VerseIndex) = followSpokenVerse(verseIndex)
            override fun onVerseFinished(verseIndex: VerseIndex) = finishSpokenVerse(verseIndex)
            override fun onChapterFinished() = speakNextChapter()
            override fun onLanguageUnavailable(language: String) {
                pauseSpeech()
                toast(getString(R.string.toast_speech_language_unavailable, languageDisplayNameWithNative(language)))
            }
            override fun onError() {
                pauseSpeech()
                toast(R.string.toast_unknown_error)
            }
        })
    }
    private val speaker by speakerDelegate
    private var installingDialog: AlertDialog? = null
    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val actions = layoutInflater.inflate(R.layout.view_reading_action_mode, null)
            intArrayOf(R.id.action_listen, R.id.action_highlight_verse, R.id.action_copy, R.id.action_share, R.id.action_bookmark_verse).forEach { id ->
                actions.findViewById<View>(id).setOnClickListener { onVerseActionClicked(id) }
            }
            mode.customView = actions
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false

        override fun onDestroyActionMode(mode: ActionMode) {
            selectedVerses.forEach { verse -> versePagerAdapter.deselectVerse(verse.verseIndex) }
            selectedVerses.clear()
            actionMode = null
        }
    }

    private fun onVerseActionClicked(actionId: Int) {
        when (actionId) {
            R.id.action_listen -> listenFromSelection()
            R.id.action_highlight_verse -> highlightSelectedVerses()
            R.id.action_copy -> copy()
            R.id.action_share -> share()
            R.id.action_bookmark_verse -> {
                viewModel.toggleBookmarks(selectedVerses.map { it.verseIndex })
                actionMode?.finish()
            }
        }
    }

    private fun currentBookName(): String {
        val status = currentReadingStatus ?: return ""
        return status.bookNames.getOrElse(status.currentVerseIndex.bookIndex) { "" }
    }

    private fun copy() {
        if (selectedVerses.isEmpty()) return

        val verses = selectedVerses.toList()
        actionMode?.finish()
        lifecycleScope.launch {
            try {
                val bookName = currentBookName()
                copyToClipBoard(
                    label = "${currentReadingStatus?.currentTranslation.orEmpty()} $bookName",
                    text = verses.toStringForCopy(bookName)
                )
                toast(R.string.toast_verses_copied)
            } catch (e: Exception) {
                Logger.withTag(tag).e(e) { "Failed to copy" }
                toast(R.string.toast_unknown_error)
            }
        }
    }

    private fun share() {
        try {
            if (selectedVerses.isEmpty()) return

            shareToSystem(
                title = getString(R.string.text_share_with),
                text = selectedVerses.toStringForCopy(currentBookName())
            )
        } catch (e: Exception) {
            Logger.withTag(tag).e(e) { "Failed to share" }
            toast(R.string.toast_unknown_error)
        } finally {
            actionMode?.finish()
        }
    }

    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var versePagerAdapter: VersePagerAdapter
    // Several reports can be in flight before the saved position comes back, so a single value could miss the match.
    private val versesReportedByScroll = mutableSetOf<VerseIndex>()
    private var userSwipingPager = false
    private var redirectingSwipe = false
    private var lastSelectedPage = -1
    // Set until the reader has moved to the referenced verse, because moving to another chapter ends the selection.
    private var pendingReferenceSelection: Pair<VerseIndex, Int>? = null
    private val referenceBackStack = ArrayDeque<VerseIndex>()

    // The launcher alias reuses a running reader, so an open-book request arrives here instead of onCreate().
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (handleSpeechIntent(intent)) return
        val openBook = intent.getIntExtra(KEY_OPEN_BOOK, -1)
        if (openBook in 0 until Bible.BOOK_COUNT) viewModel.openBook(openBook)
        openDeepLink(intent)
        selectOpenedVerse(intent)
    }

    /** Selects the current verse, as when following a cross reference, if the caller asked for it. */
    private fun selectOpenedVerse(intent: Intent) {
        if (!intent.getBooleanExtra(KEY_SELECT_VERSE, false)) return
        intent.removeExtra(KEY_SELECT_VERSE)
        lifecycleScope.launch {
            val verseIndex = viewModel.currentVerseIndex()
            if (currentReadingStatus?.currentVerseIndex == verseIndex) {
                selectReferencedVerses(verseIndex, verseIndex.verseIndex)
            } else {
                pendingReferenceSelection = verseIndex to verseIndex.verseIndex
            }
        }
    }

    /** Opens `bible://usfmbible/[<version>/]<reference>`, falling back to a search if it is not a reference. */
    private fun openDeepLink(intent: Intent) {
        val segments = intent.data?.takeIf { it.scheme == "bible" && it.host == "usfmbible" }?.pathSegments ?: return
        val reference = segments.lastOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val version = segments.getOrNull(segments.size - 2)
        lifecycleScope.launch {
            if (!viewModel.openReference(reference, version)) {
                startActivity(Intent(Intent.ACTION_SEARCH).setClass(this@ReadingActivity, SearchActivity::class.java)
                    .putExtra(SearchManager.QUERY, reference))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        savedInstanceState?.getIntArray(KEY_SPEECH_VERSE)?.takeIf { it.size == 3 }?.let(::restoreSpeech)
        handleSpeechIntent(intent)
        val openBook = intent.getIntExtra(KEY_OPEN_BOOK, -1)
        if (savedInstanceState == null && openBook in 0 until Bible.BOOK_COUNT) viewModel.openBook(openBook)
        if (savedInstanceState == null) openDeepLink(intent)
        if (savedInstanceState == null) selectOpenedVerse(intent)

        // Handle back navigation using OnBackPressedDispatcher
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewBinding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    viewBinding.drawerLayout.closeDrawer(GravityCompat.START)
                    return
                }

                if (hideVerseDetail()) {
                    return
                }

                if (referenceBackStack.isNotEmpty()) {
                    viewModel.selectVerse(referenceBackStack.removeLast())
                    return
                }

                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
    }

    override fun initializeView() = with(viewBinding) {
        drawerToggle = ActionBarDrawerToggle(this@ReadingActivity, drawerLayout, toolbarFragment.getFragment<ReadingToolbarFragment>().toolbar, 0, 0)
        drawerLayout.addDrawerListener(drawerToggle)
        speechBar.speechPlayPause.setOnClickListener {
            if (speech == Speech.PLAYING) pauseSpeech() else resumeSpeech()
        }
        speechBar.speechStop.setOnClickListener { stopSpeech() }
        speechBar.speechProgress.setOnClickListener {
            if (speech == Speech.PLAYING) pauseSpeech() else resumeSpeech()
        }
        speechBar.root.setOnClickListener {
            if (speech == Speech.PLAYING) pauseSpeech() else resumeSpeech()
        }
        speechBar.speechMenu.setOnClickListener { anchor ->
            PopupMenu(this@ReadingActivity, anchor).apply {
                inflate(R.menu.menu_speech)
                menu.findItem(R.id.action_speech_speed).title = primaryColoredSpeechSpeedTitle()
                speechTimeMenuItem = menu.findItem(R.id.action_speech_time)
                updateSpeechTime()
                setOnDismissListener { speechTimeMenuItem = null }
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_speech_language -> {
                            selectSpeechLanguage()
                            true
                        }
                        R.id.action_speech_speed -> {
                            selectSpeechSpeed()
                            true
                        }
                        else -> false
                    }
                }
                show()
            }
        }
        toolbarFragment.getFragment<ReadingToolbarFragment>().onTitleClicked = {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.closeDrawer(GravityCompat.START)
            else drawerLayout.openDrawer(GravityCompat.START)
        }
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                try {
                    chapterSelectionFragment.getFragment<ChapterSelectionFragment>().expandCurrentBook()
                } catch (e: Exception) {
                    Logger.withTag(tag).e(e) { "Error occurred while expanding current book" }
                }
            }
        })

        versePagerAdapter = VersePagerAdapter(this@ReadingActivity, viewModel::loadVerses, {
            speechRevealRequest++
            speechRevealPending = false
        }) { pagePosition, verseIndex, middleVerse ->
            // Neighbour pages report their own scroll position too; only the selected page may update the current verse.
            // The verse may belong to a neighbouring chapter that the page scrolled into.
            if (pagePosition == verseViewPager.currentItem) {
                versesReportedByScroll.add(verseIndex)
                if (versesReportedByScroll.size > 8) versesReportedByScroll.remove(versesReportedByScroll.first())
                toolbarFragment.getFragment<ReadingToolbarFragment>().showReadingChapter(verseIndex, middleVerse)
                viewModel.updateCurrentVerse(verseIndex)
            }
        }
        verseViewPager.offscreenPageLimit = 1
        verseViewPager.adapter = versePagerAdapter
        verseViewPager.makeLessSensitive()
        verseViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                when (state) {
                    ViewPager2.SCROLL_STATE_DRAGGING -> {
                        userSwipingPager = true
                        speechRevealRequest++
                        speechRevealPending = false
                        if (speech == Speech.PLAYING) pauseSpeech()
                    }
                    ViewPager2.SCROLL_STATE_IDLE -> userSwipingPager = false
                }
            }

            override fun onPageSelected(position: Int) {
                val previousPage = lastSelectedPage
                lastSelectedPage = position
                if (redirectingSwipe) return
                if (userSwipingPager && previousPage >= 0) {
                    // The page may have been scrolled into a neighbouring chapter, so a swipe moves on from the chapter being read.
                    val readingPage = currentReadingStatus?.currentVerseIndex?.takeIf { it.isValid() }?.toPagePosition()
                    val target = readingPage?.plus(position - previousPage)
                    if (target != null && target != position && target in 0 until Bible.TOTAL_CHAPTER_COUNT) {
                        redirectingSwipe = true
                        try {
                            verseViewPager.setCurrentItem(target, false)
                        } finally {
                            redirectingSwipe = false
                        }
                        lastSelectedPage = target
                        versePagerAdapter.scrollToChapterStart(target)
                        appBar.setExpanded(true, true)
                        viewModel.selectChapter(target.toBookIndex(), target.toChapterIndex())
                        return
                    }
                }
                val bookIndex = position.toBookIndex()
                val chapterIndex = position.toChapterIndex()
                if (userSwipingPager) {
                    // A swipe always lands on verse one of the new chapter.
                    versePagerAdapter.scrollToChapterStart(position)
                    appBar.setExpanded(true, true)
                    viewModel.selectChapter(bookIndex, chapterIndex)
                    return
                }
                if (speech != Speech.OFF && spokenVerse.isValid() && spokenVerse.toPagePosition() == position) {
                    viewModel.selectVerse(spokenVerse)
                    return
                }
                lifecycleScope.launch {
                    val currentVerseIndex = viewModel.currentVerseIndex()
                    if (bookIndex != currentVerseIndex.bookIndex || chapterIndex != currentVerseIndex.chapterIndex) {
                        viewModel.selectChapter(bookIndex, chapterIndex)
                    }
                }
            }
        })

        chapterSelectionFragment.getFragment<ChapterSelectionFragment>().onChapterSelected = {
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        search.setOnClickListener { startActivity(Navigator.SCREEN_SEARCH) }

        val openNoteWhenCreated = intent.getBooleanExtra(KEY_OPEN_NOTE, false)
        verseDetailView.initialize(
            onClicked = ::hideVerseDetail,
            updateNote = viewModel::saveNote,
            reorderTranslations = viewModel::reorderTranslations,
            openVerse = { verseIndex, endVerse ->
                // Remember where the reference was followed from, so that back returns there.
                viewBinding.verseDetailView.verseDetail?.verseIndex?.let { referenceBackStack.addLast(it) }
                hideVerseDetail()
                viewModel.selectVerse(verseIndex)
                if (currentReadingStatus?.currentVerseIndex == verseIndex) {
                    selectReferencedVerses(verseIndex, endVerse)
                } else {
                    pendingReferenceSelection = verseIndex to endVerse
                }
            },
            hide = !openNoteWhenCreated
        )
        if (openNoteWhenCreated) {
            lifecycleScope.launch { showVerseDetail(viewModel.currentVerseIndex(), VerseDetailViewLayout.VERSE_DETAIL_NOTE) }
        }
    }

    private fun selectSpeechSpeed() {
        val speeds = speechPlaybackSpeeds
        val labels = arrayOf(
            getString(R.string.speech_speed_050),
            getString(R.string.speech_speed_075),
            getString(R.string.speech_speed_100),
            getString(R.string.speech_speed_025),
            getString(R.string.speech_speed_150),
            getString(R.string.speech_speed_175),
            getString(R.string.speech_speed_200),
            getString(R.string.speech_speed_250),
            getString(R.string.speech_speed_300),
            getString(R.string.speech_speed_400),
            getString(R.string.speech_speed_500)
        )
        val selected = speeds.indexOf(speaker.playbackSpeed).takeIf { it >= 0 } ?: speeds.indexOf(1f)
        MaterialAlertDialogBuilder(this)
            .setTitle(primaryColoredSpeechSpeedTitle())
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                initializeSpeechRate()
                speechMillisecondsPerWord *= speaker.playbackSpeed / speeds[which]
                speaker.playbackSpeed = speeds[which]
                dialog.dismiss()
                if (speech == Speech.PLAYING) startSpeech(spokenVerse) else updateSpeechBar()
            }
            .show()
    }

    private fun primaryColoredSpeechSpeedTitle(): CharSequence {
        val title = SpannableString(getText(R.string.action_speech_speed))
        val value = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, value, true)
        title.setSpan(ForegroundColorSpan(value.data), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return title
    }

    private fun listenFromSelection() {
        val first = selectedVerses.minByOrNull { it.verseIndex.verseIndex }?.verseIndex ?: return
        actionMode?.finish()
        startSpeech(first)
    }

    /** Reads aloud from [from] to the end of its chapter, then carries on through the following chapters. */
    private fun startSpeech(from: VerseIndex) {
        initializeSpeechRate()
        speaker.stop()
        speech = Speech.PLAYING
        spokenVerse = from
        speechVerseTimer.reset()
        speechPausedFraction = 0f
        if (from !in spokenVerses) clearSpeechChapter()
        highlightSpokenVerse(from)
        viewModel.selectVerse(from)
        revealSpokenVerse(from)
        val request = ++speechRequest
        updateSpeechBar()
        lifecycleScope.launch {
            val chapter = runCatching { viewModel.speechChapter(from.bookIndex, from.chapterIndex) }.getOrNull()
            if (request != speechRequest) return@launch
            if (chapter == null) {
                pauseSpeech()
                toast(R.string.toast_unknown_error)
                return@launch
            }
            setSpeechChapter(chapter.verses)
            updateSpeechBar()
            val verses = chapter.verses.filter { it.verseIndex.verseIndex >= from.verseIndex }
            speaker.speak(verses.map { it.verseIndex to it.text.text }, chapter.language)
        }
    }

    private fun resumeSpeech() {
        if (speech == Speech.PLAYING) return
        if (spokenVerse.isValid()) {
            startSpeech(spokenVerse)
        } else {
            lifecycleScope.launch { startSpeech(viewModel.currentVerseIndex()) }
        }
    }

    private fun pauseSpeech() {
        if (speech != Speech.PLAYING) return
        speechPausedFraction = currentSpeechVerseFraction()
        speech = Speech.PAUSED
        speechRequest++
        if (speakerDelegate.isInitialized()) speaker.stop()
        updateSpeechBar()
    }

    private fun stopSpeech() {
        speech = Speech.OFF
        speechRequest++
        if (speakerDelegate.isInitialized()) speaker.stop()
        highlightSpokenVerse(null)
        clearSpeechChapter()
        speechRevealRequest++
        speechRevealPending = false
        speechVerseTimer.reset()
        updateSpeechBar()
    }

    /** A user-selected verse becomes the resume point, but pauses playback so the change is noticeable. */
    private fun moveSpeech(verseIndex: VerseIndex) {
        if (verseIndex == spokenVerse) return
        if (speech == Speech.PLAYING) pauseSpeech()
        spokenVerse = verseIndex
        speechVerseTimer.reset()
        speechPausedFraction = 0f
        highlightSpokenVerse(verseIndex)
        if (verseIndex !in spokenVerses) clearSpeechChapter()
        updateSpeechBar()
        if (verseIndex !in spokenVerses) {
            val request = ++speechRequest
            lifecycleScope.launch {
                val chapter = runCatching { viewModel.speechChapter(verseIndex.bookIndex, verseIndex.chapterIndex) }.getOrNull()
                if (request != speechRequest || speech != Speech.PAUSED || spokenVerse != verseIndex || chapter == null) return@launch
                setSpeechChapter(chapter.verses)
                updateSpeechBar()
            }
        }
    }

    private fun highlightSpokenVerse(verseIndex: VerseIndex?) {
        spokenHighlight?.let(versePagerAdapter::deselectVerse)
        spokenHighlight = verseIndex
        verseIndex?.let(versePagerAdapter::selectVerse)
    }

    private fun speakNextChapter() {
        if (speech != Speech.PLAYING) return
        val next = adjacentSpeechChapter(spokenVerse, 1) ?: return stopSpeech()
        startSpeech(next)
    }

    private fun skipSpeechChapter(direction: Int) {
        if (speech == Speech.OFF) return
        val next = adjacentSpeechChapter(spokenVerse, direction) ?: return
        if (speech == Speech.PLAYING) {
            startSpeech(next)
        } else {
            moveSpeech(next)
            viewModel.selectVerse(next)
            revealSpokenVerse(next)
        }
    }

    private fun followSpokenVerse(verseIndex: VerseIndex, started: Boolean = true) {
        if (speech != Speech.PLAYING) return
        val now = SystemClock.elapsedRealtime()
        if (spokenVerse != verseIndex) {
            speechPausedFraction = 0f
        }
        if (started) speechVerseTimer.started(verseIndex, now) else speechVerseTimer.queued(verseIndex, now)
        val changed = spokenVerse != verseIndex
        spokenVerse = verseIndex
        if (changed) highlightSpokenVerse(verseIndex)
        updateSpeechBar()
        if (changed) {
            // The reading position is saved as the verse changes, so it survives a rotation.
            viewModel.selectVerse(verseIndex)
            revealSpokenVerse(verseIndex)
        }
    }

    private fun finishSpokenVerse(verseIndex: VerseIndex) {
        val elapsed = speechVerseTimer.elapsed(verseIndex, SystemClock.elapsedRealtime()) ?: return
        val words = speechWordsByVerse[verseIndex] ?: return
        val observedRate = (elapsed.toFloat() / words).coerceIn(80f, 1500f)
        speechMillisecondsPerWord = (speechMillisecondsPerWord * 3f + observedRate) / 4f
    }

    private fun revealSpokenVerse(verseIndex: VerseIndex) {
        val request = ++speechRevealRequest
        speechRevealPending = true
        val page = verseIndex.toPagePosition()
        if (!versePagerAdapter.containsChapterInCurrentPage(verseIndex) && viewBinding.verseViewPager.currentItem != page) {
            viewBinding.verseViewPager.setCurrentItem(page, false)
        }
        fun retry(remaining: Int) {
            if (request != speechRevealRequest || spokenVerse != verseIndex || speech == Speech.OFF) return
            viewBinding.verseViewPager.post {
                if (request != speechRevealRequest || spokenVerse != verseIndex || speech == Speech.OFF) return@post
                if (versePagerAdapter.revealVerse(verseIndex)) {
                    speechRevealPending = false
                } else if (remaining > 0) {
                    viewBinding.verseViewPager.postDelayed({ retry(remaining - 1) }, 100)
                }
            }
        }
        retry(10)
    }

    private fun countSpeechWords(text: String): Int =
        Regex("[\\p{L}\\p{N}]+").findAll(text).count().coerceAtLeast(1)

    private fun clearSpeechChapter() {
        spokenVerses = emptyList()
        speechWordsByVerse = emptyMap()
        speechTotalWords = 0
    }

    private fun setSpeechChapter(verses: List<Verse>) {
        initializeSpeechRate()
        spokenVerses = verses.map { it.verseIndex }
        speechWordsByVerse = verses.associate { it.verseIndex to countSpeechWords(it.text.text) }
        speechTotalWords = speechWordsByVerse.values.sum()
    }

    private fun initializeSpeechRate() {
        if (!speechRateInitialized) {
            speechMillisecondsPerWord /= speaker.playbackSpeed
            speechRateInitialized = true
        }
    }

    private fun currentSpeechVerseFraction(): Float {
        val words = speechWordsByVerse[spokenVerse] ?: return speechPausedFraction
        return speechVerseTimer.fraction(spokenVerse, SystemClock.elapsedRealtime(), words, speechMillisecondsPerWord)
    }

    private fun updateSpeechProgress() {
        viewBinding.speechBar.speechProgress.progress = (speechFraction() * 1000).toInt()
        updateSpeechTime()
    }

    private fun updateSpeechTime() {
        speechTimeMenuItem?.title = if (speechChapterDuration == 0L) {
            getString(R.string.speech_time_unknown)
        } else {
            getString(R.string.speech_time_estimate,
                DateUtils.formatElapsedTime(speechPosition() / 1000),
                DateUtils.formatElapsedTime(speechChapterDuration / 1000))
        }
    }

    private fun speechFraction(): Float = chapterSpeechFraction(
        spokenVerses, speechWordsByVerse, spokenVerse,
        if (speech == Speech.PLAYING) currentSpeechVerseFraction() else speechPausedFraction
    )

    private fun speechPosition(): Long = (speechChapterDuration * speechFraction()).toLong()

    private fun updateSpeechBar() = with(viewBinding.speechBar) {
        root.visibility = if (speech == Speech.OFF) View.GONE else View.VISIBLE
        val playing = speech == Speech.PLAYING
        speechPlayPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        speechPlayPause.contentDescription = getString(if (playing) R.string.action_pause else R.string.action_play)
        speechStop.visibility = if (speech == Speech.PAUSED) View.VISIBLE else View.GONE
        speechProgress.max = 1000
        updateSpeechProgress()
        speechProgressHandler.removeCallbacks(speechProgressTick)
        if (playing) speechProgressHandler.post(speechProgressTick)
        window.decorView.keepScreenOn = playing || settings?.keepScreenOn == true
        if (speech != Speech.OFF) viewBinding.search.hide() else if (settings?.hideSearchButton != true) viewBinding.search.show()
        updateSpeechNotification()
    }

    private fun updateSpeechNotification() {
        if (speech == Speech.OFF) {
            if (speechNotificationDelegate.isInitialized()) speechNotification.hide()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (speech == Speech.PLAYING && !notificationPermissionRequested) {
                notificationPermissionRequested = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val book = currentReadingStatus?.bookNames?.getOrNull(spokenVerse.bookIndex).orEmpty().ifEmpty { getString(R.string.text_bible) }
        val title = getString(
            R.string.speech_notification_title,
            book,
            spokenVerse.chapterIndex + 1,
            spokenVerse.verseIndex + 1
        )
        speechNotification.update(
            title, speech == Speech.PLAYING, speechChapterDuration, speechPosition(),
            adjacentSpeechChapter(spokenVerse, -1) != null, adjacentSpeechChapter(spokenVerse, 1) != null
        )
    }

    private fun handleSpeechIntent(intent: Intent): Boolean = when (intent.action) {
        SpeechNotification.ACTION_OPEN -> {
            intent.action = null
            true
        }
        SpeechNotification.ACTION_PLAY -> {
            intent.action = null
            resumeSpeech()
            true
        }
        SpeechNotification.ACTION_PAUSE -> {
            intent.action = null
            pauseSpeech()
            true
        }
        SpeechNotification.ACTION_PREVIOUS -> {
            intent.action = null
            skipSpeechChapter(-1)
            true
        }
        SpeechNotification.ACTION_NEXT -> {
            intent.action = null
            skipSpeechChapter(1)
            true
        }
        else -> false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (speech != Speech.OFF && spokenVerse.isValid()) {
            outState.putIntArray(KEY_SPEECH_VERSE, intArrayOf(spokenVerse.bookIndex, spokenVerse.chapterIndex, spokenVerse.verseIndex))
        }
    }

    // After a rotation the reader is where the speech got to, paused so the user can press play again.
    private fun restoreSpeech(saved: IntArray) {
        val verse = VerseIndex(saved[0], saved[1], saved[2])
        speech = Speech.PAUSED
        spokenVerse = verse
        speechVerseTimer.reset()
        speechPausedFraction = 0f
        highlightSpokenVerse(verse)
        updateSpeechBar()
        revealSpokenVerse(verse)
        lifecycleScope.launch {
            val chapter = runCatching { viewModel.speechChapter(verse.bookIndex, verse.chapterIndex) }.getOrNull() ?: return@launch
            if (speech == Speech.PAUSED && spokenVerse == verse) {
                setSpeechChapter(chapter.verses)
                updateSpeechBar()
            }
        }
    }

    private fun selectSpeechLanguage() {
        speaker.availableLanguages { locales ->
            val tags = listOf<String?>(null) + locales.map(Locale::toLanguageTag)
            val names = listOf(getString(R.string.speech_language_automatic)) + locales.map { it.displayName }
            listDialog(R.string.action_speech_language, names.toTypedArray(), tags.indexOf(speaker.selectedLanguage).coerceAtLeast(0), { which ->
                speaker.selectedLanguage = tags[which]
                if (speech == Speech.PLAYING) startSpeech(spokenVerse)
            })
        }
    }

    private fun startActivity(@Navigator.Companion.Screen screen: Int, extras: Bundle? = null) {
        try {
            navigator.navigate(this, screen, extras)
        } catch (e: Exception) {
            Logger.withTag(tag).e(e) { "Failed to open activity" }
            dialog(true, R.string.dialog_title_error, R.string.dialog_message_failed_to_navigate, { _, _ -> startActivity(screen) })
        }
    }

    override fun onViewActionEmitted(viewAction: ReadingViewModel.ViewAction) {
        when (viewAction) {
            is ReadingViewModel.ViewAction.ShowVerses -> {
                versePagerAdapter.setVerses(viewAction.translation, viewAction.bookIndex, viewAction.chapterIndex, viewAction.items)
                // A selection made while the chapter was loading has not reached its items.
                selectedVerses.filter { it.verseIndex.bookIndex == viewAction.bookIndex && it.verseIndex.chapterIndex == viewAction.chapterIndex }
                    .forEach { versePagerAdapter.selectVerse(it.verseIndex) }
                if (speech != Speech.OFF && spokenVerse.bookIndex == viewAction.bookIndex &&
                    spokenVerse.chapterIndex == viewAction.chapterIndex) {
                    versePagerAdapter.selectVerse(spokenVerse)
                    if (speechRevealPending) revealSpokenVerse(spokenVerse)
                }
            }
            is ReadingViewModel.ViewAction.ShowVerseDetail -> with(viewBinding.verseDetailView) {
                verseDetail = viewAction.verseDetail
                show(viewAction.content)
            }
            is ReadingViewModel.ViewAction.UpdateVerse -> versePagerAdapter.notifyVerseUpdate(viewAction.update)
            is ReadingViewModel.ViewAction.BookmarkSaved, is ReadingViewModel.ViewAction.HighlightSaved -> Unit
            is ReadingViewModel.ViewAction.NoteSaved -> viewBinding.verseDetailView.setNote(viewAction.note)
        }
    }

    override fun onViewStateUpdated(viewState: ReadingViewModel.ViewState) {
        if (viewState.settings != settings) {
            applySettings(viewState.settings)
        }
        if (viewState.currentReadingStatus != currentReadingStatus) {
            viewState.currentReadingStatus?.let { applyCurrentReadingStatus(it) }
        }

        if (viewState.installingTranslation) {
            if (installingDialog == null) installingDialog = indeterminateProgressDialog(R.string.dialog_title_installing)
        } else {
            installingDialog?.dismiss()
            installingDialog = null
        }

        viewState.error?.handle()
    }

    private fun applySettings(settings: Settings) {
        this.settings = settings

        updateSpeechBar()

        versePagerAdapter.settings = settings
        viewBinding.verseDetailView.setSettings(settings)
    }

    private fun applyCurrentReadingStatus(status: ReadingViewModel.ViewState.CurrentReadingStatus) {
        val previousVerseIndex = currentReadingStatus?.currentVerseIndex ?: VerseIndex.INVALID
        currentReadingStatus = status

        with(viewBinding) {
            versePagerAdapter.setCurrent(
                verseIndex = status.currentVerseIndex,
                translation = status.currentTranslation,
                parallel = status.parallelTranslations
            )
            // A verse reached by the user scrolling is already on screen; moving the pager would jump it to a chapter heading.
            val reportedByScroll = status.currentVerseIndex in versesReportedByScroll
            if (reportedByScroll) versesReportedByScroll.clear()
            val chapterChanged = previousVerseIndex.bookIndex != status.currentVerseIndex.bookIndex
                || previousVerseIndex.chapterIndex != status.currentVerseIndex.chapterIndex
            val speechFollowing = speech != Speech.OFF && status.currentVerseIndex == spokenVerse
            if (!speechFollowing && !reportedByScroll && (chapterChanged || !versePagerAdapter.containsChapterInCurrentPage(status.currentVerseIndex))) {
                val targetPage = status.currentVerseIndex.toPagePosition()
                if (verseViewPager.currentItem != targetPage) verseViewPager.setCurrentItem(targetPage, false)
                // A cached page is reused without a rebind, so it must be positioned explicitly.
                val verse = status.currentVerseIndex
                verseViewPager.post { versePagerAdapter.scrollToVerse(verse) }
            }
        }
        hideVerseDetail()

        if (previousVerseIndex.bookIndex != status.currentVerseIndex.bookIndex
            || previousVerseIndex.chapterIndex != status.currentVerseIndex.chapterIndex) {
            actionMode?.finish()
        }

        pendingReferenceSelection?.takeIf { it.first == status.currentVerseIndex }?.let { (verseIndex, endVerse) ->
            pendingReferenceSelection = null
            selectReferencedVerses(verseIndex, endVerse)
        }
    }

    private fun showNoTranslationDownloadedDialog(error: ReadingViewModel.ViewState.Error) {
        if (isDestroyed) return

        val content = layoutInflater.inflate(R.layout.dialog_quick_download_translations, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setCancelable(false)
            .setTitle(R.string.dialog_title_no_translation_downloaded)
            .setMessage(R.string.dialog_message_download_translation_confirmation)
            .setView(content)
            .setPositiveButton(android.R.string.ok) { _, _ -> startActivity(Navigator.SCREEN_TRANSLATIONS) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnDismissListener { viewModel.markErrorAsShown(error) }
            .create()
        listOf(
            R.id.button_download_engbsb to "engbsb",
            R.id.button_download_engwebp to "engwebp",
            R.id.button_download_engerv to "engerv",
        ).forEach { (buttonId, shortName) ->
            content.findViewById<View>(buttonId).setOnClickListener {
                dialog.dismiss()
                startActivity(Navigator.SCREEN_TRANSLATIONS, Bundle().apply { putString(TranslationsActivity.KEY_DOWNLOAD_TRANSLATION, shortName) })
            }
        }
        dialog.show()
    }

    private fun ReadingViewModel.ViewState.Error.handle() {
        when (this) {
            is ReadingViewModel.ViewState.Error.NoTranslationDownloadedError -> showNoTranslationDownloadedDialog(this)
            is ReadingViewModel.ViewState.Error.TranslationSelectionError -> showErrorDialog(R.string.dialog_message_failed_to_select_translation) {
                viewModel.selectTranslation(translationToSelect)
            }
            is ReadingViewModel.ViewState.Error.ChapterSelectionError -> showErrorDialog(R.string.dialog_message_failed_to_select_chapter) {
                viewModel.selectChapter(bookIndex, chapterIndex)
            }
            is ReadingViewModel.ViewState.Error.VerseSelectionError -> showErrorDialog(R.string.dialog_message_failed_to_select_verse) {
                viewModel.selectVerse(verseToSelect)
            }
            is ReadingViewModel.ViewState.Error.VersesLoadingError -> {
                versePagerAdapter.onVersesLoadFailed(bookIndex, chapterIndex)
                showErrorDialog(R.string.dialog_message_failed_to_load_verses) {
                    viewModel.loadVerses(bookIndex, chapterIndex)
                }
            }
            is ReadingViewModel.ViewState.Error.VerseDetailLoadingError -> showErrorDialog(R.string.dialog_message_failed_to_load_verse_detail) {
                viewModel.loadVerseDetail(verseIndex, content)
            }
            is ReadingViewModel.ViewState.Error.BookmarkSavingError,
            is ReadingViewModel.ViewState.Error.HighlightSavingError,
            is ReadingViewModel.ViewState.Error.NoteSavingError -> {
                toast(R.string.toast_unknown_error)
                viewModel.markErrorAsShown(this)
            }
        }
    }

    private fun ReadingViewModel.ViewState.Error.showErrorDialog(@StringRes message: Int, onRetry: () -> Unit) {
        dialog(
            cancelable = true,
            title = R.string.dialog_title_error,
            message = message,
            onPositive = { _, _ -> onRetry() },
            onDismiss = { viewModel.markErrorAsShown(this) },
        )
    }

    private fun hideVerseDetail(): Boolean {
        val verseIndex = viewBinding.verseDetailView.verseDetail?.verseIndex ?: return false
        return if (viewBinding.verseDetailView.hide()) {
            viewModel.updateVerse(VerseUpdate(verseIndex, VerseUpdate.VERSE_DESELECTED))
            true
        } else {
            false
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        drawerToggle.syncState()
    }

    override fun onStart() {
        super.onStart()
        viewModel.checkDownloadedTranslation()
    }

    override fun onDestroy() {
        speechProgressHandler.removeCallbacks(speechProgressTick)
        if (speakerDelegate.isInitialized()) speaker.shutdown()
        if (speechNotificationDelegate.isInitialized()) speechNotification.release()
        installingDialog?.dismiss()
        installingDialog = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        viewModel.startTrackingReadingProgress()
    }

    override fun onPause() {
        viewModel.stopTrackingReadingProgress()
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle.onConfigurationChanged(newConfig)
    }

    override fun onVerseClicked(verse: Verse) {
        if (speech != Speech.OFF && actionMode == null) {
            if (verse.verseIndex != spokenVerse) {
                viewModel.recordUserSelectedVerse(verse.verseIndex)
                moveSpeech(verse.verseIndex)
            }
            return
        }
        if (actionMode == null) {
            actionMode = startSupportActionMode(actionModeCallback)
        }

        if (selectedVerses.contains(verse)) {
            // de-select the verse
            selectedVerses.remove(verse)
            if (selectedVerses.isEmpty()) {
                actionMode?.finish()
            }

            versePagerAdapter.deselectVerse(verse.verseIndex)
        } else {
            // select the verse
            selectedVerses.add(verse)
            versePagerAdapter.selectVerse(verse.verseIndex)
            viewModel.recordUserSelectedVerse(verse.verseIndex)
        }
    }

    private fun selectReferencedVerses(verseIndex: VerseIndex, endVerse: Int) {
        lifecycleScope.launch {
            try {
                val verses = viewModel.versesInRange(verseIndex, endVerse)
                if (verses.isEmpty()) return@launch
                actionMode?.finish()
                actionMode = startSupportActionMode(actionModeCallback)
                verses.forEach { verse ->
                    selectedVerses.add(verse)
                    versePagerAdapter.selectVerse(verse.verseIndex)
                    viewModel.recordUserSelectedVerse(verse.verseIndex)
                }
            } catch (e: Exception) {
                Logger.withTag(tag).e(e) { "Failed to select the referenced verses" }
            }
        }
    }

    private fun showVerseDetail(verseIndex: VerseIndex, @VerseDetailViewLayout.Companion.VerseDetail content: Int) {
        viewModel.loadVerseDetail(verseIndex, content)
    }

    override fun onVerseLongClicked(verse: Verse) {
        showVerseDetail(verse.verseIndex, VerseDetailViewLayout.VERSE_DETAIL_VERSES)
    }

    private fun highlightSelectedVerses() {
        val verseIndexes = selectedVerses.map { it.verseIndex }
        actionMode?.finish()

        val defaultHighlightColor = settings?.defaultHighlightColor ?: Highlight.COLOR_NONE
        if (Highlight.COLOR_NONE == defaultHighlightColor) {
            listDialog(
                title = R.string.text_pick_highlight_color,
                items = Highlight.AVAILABLE_COLORS.map { highlightSwatch(it) }.toTypedArray(),
                selected = 0,
                onSelected = { which -> verseIndexes.forEach { viewModel.saveHighlight(it, Highlight.AVAILABLE_COLORS[which]) } }
            )
        } else {
            verseIndexes.forEach { viewModel.saveHighlight(it, defaultHighlightColor) }
        }
    }
    fun onNoteClicked(verseIndex: VerseIndex) {
        showVerseDetail(verseIndex, VerseDetailViewLayout.VERSE_DETAIL_NOTE)
    }

    override fun onVerseTextClicked(translation: String) {
        viewModel.selectTranslation(translation)
    }

    override fun onVerseTextLongClicked(verse: Verse) {
        lifecycleScope.launch {
            try {
                val bookName = viewModel.bookName(verse.text.translationShortName, verse.verseIndex.bookIndex)
                copyToClipBoard(
                    label = "${verse.text.translationShortName} $bookName",
                    text = verse.toStringForCopy(bookName)
                )
                toast(R.string.toast_verses_copied)
            } catch (e: Exception) {
                Logger.withTag(tag).e(e) { "Failed to copy" }
                toast(R.string.toast_unknown_error)
            }
        }
    }
}
