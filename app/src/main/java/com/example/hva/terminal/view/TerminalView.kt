package com.example.hva.terminal.view

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.SystemClock
import android.text.InputType
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.example.hva.input.KeyMapper
import com.example.hva.session.TerminalSession
import com.example.hva.terminal.TerminalColor
import com.example.hva.terminal.TerminalLine

/**
 * High-performance hardware-accelerated Canvas Terminal View.
 * Implements run-length background batching, string-run text batching,
 * dirty row clipping, vsync-throttled redraws, smooth scrollback,
 * pinch-to-zoom font scaling, and IME input connection.
 */
class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var session: TerminalSession? = null
        set(value) {
            field = value
            value?.emulator?.onRedrawNeeded = {
                requestThrottledRedraw()
            }
            requestLayout()
            invalidate()
        }

    // Monospace Font & Metrics
    private var fontSizePx: Float = 40f
    private var charWidth: Float = 24f
    private var charHeight: Float = 48f
    private var charAscent: Float = 36f

    // Paints
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = fontSizePx
        isSubpixelText = true
        isLinearText = true
        hinting = Paint.HINTING_ON
    }
    private val bgPaint = Paint()
    private val cursorPaint = Paint()
    private val selectionPaint = Paint().apply {
        color = TerminalColor.SELECTION_BG
    }

    // Cursor appearance
    enum class CursorStyle { BLOCK, BEAM, UNDERLINE }
    var cursorStyle = CursorStyle.BLOCK
    var cursorBlinkEnabled = true
    private var cursorVisibleState = true
    private var lastCursorBlinkTime = 0L

    // Scrollback offset (0 = live screen bottom, >0 = scrolled up into history)
    var scrollOffset = 0
        private set

    private var scrollAccumulatorY = 0f
    private val scroller = android.widget.OverScroller(context)
    private val flingRunnable = object : Runnable {
        override fun run() {
            if (scroller.computeScrollOffset()) {
                val maxScroll = session?.emulator?.screen?.getScrollbackSize() ?: 0
                val newOffset = (scroller.currY / charHeight).toInt().coerceIn(0, maxScroll)
                if (newOffset != scrollOffset) {
                    scrollOffset = newOffset
                    invalidate()
                }
                if (!scroller.isFinished) {
                    postOnAnimation(this)
                }
            }
        }
    }

    // Selection
    private var isSelecting = false
    private var selStartRow = 0
    private var selStartCol = 0
    private var selEndRow = 0
    private var selEndCol = 0

    // Modifiers latch state (controlled by ExtraKeysToolbar)
    var ctrlLatched = false
    var altLatched = false

    // Vsync redraw throttling to prevent ANR during `seq 1 100000`
    private var redrawPending = false

    // Gestures
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (isSelecting) return false
            scroller.forceFinished(true)
            scrollAccumulatorY += distanceY
            val deltaLines = (scrollAccumulatorY / charHeight).toInt()
            if (deltaLines != 0) {
                val maxScroll = session?.emulator?.screen?.getScrollbackSize() ?: 0
                val newOffset = (scrollOffset + deltaLines).coerceIn(0, maxScroll)
                if (newOffset != scrollOffset) {
                    scrollOffset = newOffset
                    invalidate()
                }
                scrollAccumulatorY -= deltaLines * charHeight
            }
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (isSelecting) return false
            val maxScroll = session?.emulator?.screen?.getScrollbackSize() ?: 0
            if (maxScroll <= 0) return false

            val startY = (scrollOffset * charHeight).toInt()
            val maxY = (maxScroll * charHeight).toInt()
            scroller.forceFinished(true)
            scroller.fling(0, startY, 0, -velocityY.toInt(), 0, 0, 0, maxY)
            postOnAnimation(flingRunnable)
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            showSoftKeyboard()
            if (isSelecting) {
                isSelecting = false
                invalidate()
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val s = session ?: return false
            val screen = s.emulator.screen
            val col = (e.x / charWidth).toInt().coerceIn(0, screen.columns - 1)
            val row = (e.y / charHeight).toInt().coerceIn(0, screen.rows - 1)
            val line = screen.getLineAt(row, scrollOffset) ?: return false

            if (col < line.chars.size && line.chars[col] != ' ') {
                var startCol = col
                while (startCol > 0 && line.chars[startCol - 1] != ' ' && !isDelimiter(line.chars[startCol - 1])) {
                    startCol--
                }
                var endCol = col
                while (endCol < screen.columns - 1 && line.chars[endCol + 1] != ' ' && !isDelimiter(line.chars[endCol + 1])) {
                    endCol++
                }
                isSelecting = true
                selStartRow = row
                selEndRow = row
                selStartCol = startCol
                selEndCol = endCol
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                invalidate()
                return true
            }
            return false
        }

        override fun onLongPress(e: MotionEvent) {
            val s = session ?: return
            val col = (e.x / charWidth).toInt().coerceIn(0, (s.emulator.screen.columns - 1).coerceAtLeast(0))
            val row = (e.y / charHeight).toInt().coerceIn(0, (s.emulator.screen.rows - 1).coerceAtLeast(0))
            isSelecting = true
            selStartCol = col
            selStartRow = row
            selEndCol = col
            selEndRow = row
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            invalidate()
        }
    })

    private fun isDelimiter(ch: Char): Boolean {
        return ch in " \t\r\n`~!@#$%^&*()=+[{]}\\|;:'\",<>/?\""
    }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val factor = detector.scaleFactor
            val newSize = (fontSizePx * factor).coerceIn(20f, 90f)
            if (kotlin.math.abs(newSize - fontSizePx) > 1f) {
                setFontSize(newSize)
            }
            return true
        }
    })

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        updateFontMetrics()
    }

    fun setFontSize(sizePx: Float) {
        fontSizePx = sizePx
        textPaint.textSize = fontSizePx
        updateFontMetrics()
        requestLayout()
        invalidate()
    }

    private fun updateFontMetrics() {
        val fm = textPaint.fontMetrics
        charHeight = kotlin.math.ceil(fm.descent - fm.ascent)
        charAscent = -fm.ascent
        charWidth = textPaint.measureText("M")
        if (charWidth <= 0f) charWidth = 20f
        if (charHeight <= 0f) charHeight = 40f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && charWidth > 0 && charHeight > 0) {
            val cols = (w / charWidth).toInt().coerceAtLeast(20)
            val rows = (h / charHeight).toInt().coerceAtLeast(5)
            session?.resize(cols, rows)
        }
    }

    fun requestThrottledRedraw() {
        if (!redrawPending) {
            redrawPending = true
            postOnAnimation {
                redrawPending = false
                invalidate()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = session ?: return
        val screen = s.emulator.screen

        screen.lock.lock()
        try {
            val cols = screen.columns
            val rows = screen.rows
            val totalScroll = screen.getScrollbackSize()

            // 1. Fill Default Background
            bgPaint.color = TerminalColor.DEFAULT_BG
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // 2. Draw Rows
            val now = SystemClock.uptimeMillis()
            if (cursorBlinkEnabled && now - lastCursorBlinkTime > 500) {
                cursorVisibleState = !cursorVisibleState
                lastCursorBlinkTime = now
            }

            for (r in 0 until rows) {
                val yTop = r * charHeight
                val yBaseline = yTop + charAscent
                val line: TerminalLine = screen.getLineAt(r, scrollOffset) ?: continue

                // Background Run-length Batching
                var runStart = 0
                var currentBg = line.bgColors[0]

                for (c in 1 until cols) {
                    val bg = line.bgColors[c]
                    if (bg != currentBg) {
                        if (currentBg != TerminalColor.DEFAULT_BG) {
                            bgPaint.color = currentBg
                            canvas.drawRect(
                                runStart * charWidth,
                                yTop,
                                c * charWidth,
                                yTop + charHeight,
                                bgPaint
                            )
                        }
                        runStart = c
                        currentBg = bg
                    }
                }
                if (currentBg != TerminalColor.DEFAULT_BG) {
                    bgPaint.color = currentBg
                    canvas.drawRect(
                        runStart * charWidth,
                        yTop,
                        cols * charWidth,
                        yTop + charHeight,
                        bgPaint
                    )
                }

                // Selection highlight
                if (isSelecting) {
                    val minR = minOf(selStartRow, selEndRow)
                    val maxR = maxOf(selStartRow, selEndRow)
                    if (r in minR..maxR) {
                        val c1 = if (r == minR) minOf(selStartCol, selEndCol) else 0
                        val c2 = if (r == maxR) maxOf(selStartCol, selEndCol) else cols - 1
                        canvas.drawRect(
                            c1 * charWidth,
                            yTop,
                            (c2 + 1) * charWidth,
                            yTop + charHeight,
                            selectionPaint
                        )
                    }
                }

                // Text Run-length Batching
                var textStart = 0
                var currentFg = line.fgColors[0]
                var currentAttr = line.attributes[0]

                for (c in 1 until cols) {
                    val fg = line.fgColors[c]
                    val attr = line.attributes[c]
                    if (fg != currentFg || attr != currentAttr) {
                        drawTextRun(canvas, line.chars, textStart, c - textStart, currentFg, currentAttr, textStart * charWidth, yBaseline)
                        textStart = c
                        currentFg = fg
                        currentAttr = attr
                    }
                }
                drawTextRun(canvas, line.chars, textStart, cols - textStart, currentFg, currentAttr, textStart * charWidth, yBaseline)
            }

            // 3. Draw Cursor (if at live view)
            if (scrollOffset == 0 && screen.cursorVisible && (!cursorBlinkEnabled || cursorVisibleState)) {
                val cx = screen.cursorX.coerceIn(0, cols - 1)
                val cy = screen.cursorY.coerceIn(0, rows - 1)
                val x = cx * charWidth
                val y = cy * charHeight

                cursorPaint.color = TerminalColor.CURSOR_COLOR
                when (cursorStyle) {
                    CursorStyle.BLOCK -> {
                        cursorPaint.style = Paint.Style.FILL
                        cursorPaint.alpha = 180
                        canvas.drawRect(x, y, x + charWidth, y + charHeight, cursorPaint)
                    }
                    CursorStyle.BEAM -> {
                        cursorPaint.style = Paint.Style.FILL
                        cursorPaint.alpha = 255
                        canvas.drawRect(x, y, x + 3f, y + charHeight, cursorPaint)
                    }
                    CursorStyle.UNDERLINE -> {
                        cursorPaint.style = Paint.Style.FILL
                        cursorPaint.alpha = 255
                        canvas.drawRect(x, y + charHeight - 4f, x + charWidth, y + charHeight, cursorPaint)
                    }
                }
            }

            // Schedule continuous blink update if blink is enabled
            if (cursorBlinkEnabled && scrollOffset == 0) {
                postInvalidateDelayed(500)
            }

        } finally {
            screen.lock.unlock()
        }
    }

    private fun drawTextRun(
        canvas: Canvas,
        chars: CharArray,
        start: Int,
        count: Int,
        fg: Int,
        attr: Byte,
        x: Float,
        y: Float
    ) {
        if (count <= 0) return

        // Skip runs that are purely spaces
        var onlySpaces = true
        for (i in start until start + count) {
            if (chars[i] != ' ') {
                onlySpaces = false
                break
            }
        }
        if (onlySpaces) return

        textPaint.color = fg
        val isBold = (attr.toInt() and TerminalLine.ATTR_BOLD.toInt()) != 0
        val isItalic = (attr.toInt() and TerminalLine.ATTR_ITALIC.toInt()) != 0
        val isUnderline = (attr.toInt() and TerminalLine.ATTR_UNDERLINE.toInt()) != 0
        val isStrike = (attr.toInt() and TerminalLine.ATTR_STRIKE.toInt()) != 0

        textPaint.isFakeBoldText = isBold
        textPaint.textSkewX = if (isItalic) -0.25f else 0f
        textPaint.isUnderlineText = isUnderline
        textPaint.isStrikeThruText = isStrike

        canvas.drawText(chars, start, count, x, y, textPaint)
    }

    // Touch & Key Input Handling
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            scroller.forceFinished(true)
            scrollAccumulatorY = 0f
        }
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        if (isSelecting && event.pointerCount == 1) {
            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    val s = session ?: return true
                    val col = (event.x / charWidth).toInt().coerceIn(0, s.emulator.screen.columns - 1)
                    val row = (event.y / charHeight).toInt().coerceIn(0, s.emulator.screen.rows - 1)
                    if (col != selEndCol || row != selEndRow) {
                        selEndCol = col
                        selEndRow = row
                        invalidate()
                    }
                }
            }
        }
        return true
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_NULL
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_FULLSCREEN

        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (!text.isNullOrEmpty()) {
                    val s = session ?: return true
                    for (ch in text) {
                        if (ctrlLatched) {
                            s.write(KeyMapper.getControlCode(ch))
                            ctrlLatched = false
                        } else if (altLatched) {
                            s.write(byteArrayOf(0x1B, ch.code.toByte()))
                            altLatched = false
                        } else {
                            s.write(ch.toString().toByteArray(Charsets.UTF_8))
                        }
                    }
                    scrollOffset = 0
                }
                return true
            }

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                return commitText(text, newCursorPosition)
            }

            override fun finishComposingText(): Boolean {
                return true
            }

            override fun performEditorAction(actionCode: Int): Boolean {
                val s = session ?: return true
                s.write(byteArrayOf('\r'.code.toByte()))
                scrollOffset = 0
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                val s = session ?: return true
                for (i in 0 until beforeLength) {
                    s.write(byteArrayOf(0x7F)) // DEL
                }
                scrollOffset = 0
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    return onKeyDown(event.keyCode, event)
                }
                return super.sendKeyEvent(event)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val s = session ?: return super.onKeyDown(keyCode, event)
        val seq = KeyMapper.getSequenceForKey(
            keyCode,
            event,
            s.emulator.screen.applicationCursorKeys,
            ctrlLatched,
            altLatched
        )
        if (seq != null) {
            s.write(seq)
            ctrlLatched = false
            altLatched = false
            scrollOffset = 0
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    fun showSoftKeyboard() {
        requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideSoftKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(windowToken, 0)
    }

    fun toggleSoftKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    fun selectAll() {
        val s = session ?: return
        isSelecting = true
        selStartRow = 0
        selStartCol = 0
        selEndRow = s.emulator.screen.rows - 1
        selEndCol = s.emulator.screen.columns - 1
        invalidate()
    }

    fun copySelection(): String? {
        val s = session ?: return null
        val text = if (isSelecting) {
            s.emulator.screen.getSelectedText(
                selStartRow, selStartCol,
                selEndRow, selEndCol,
                scrollOffset
            )
        } else {
            // If no active selection, copy the active visible screen
            s.emulator.screen.getSelectedText(
                0, 0,
                s.emulator.screen.rows - 1, s.emulator.screen.columns - 1,
                scrollOffset
            )
        }

        if (!text.isNullOrEmpty()) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(android.content.ClipData.newPlainText("Hva Terminal", text))
            val preview = text.trim().replace("\n", " ").take(30)
            android.widget.Toast.makeText(context, "Copié : \"$preview...\"", android.widget.Toast.LENGTH_SHORT).show()
        }
        isSelecting = false
        invalidate()
        return text
    }

    fun pasteClipboard(): Boolean {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = cm?.primaryClip?.getItemAt(0)?.text?.toString()
        if (!clip.isNullOrEmpty()) {
            val bracketed = session?.emulator?.screen?.bracketedPasteMode == true
            session?.write(KeyMapper.formatPaste(clip, bracketed))
            scrollOffset = 0
            return true
        }
        return false
    }

    fun sendCtrl(ch: Char) {
        session?.write(KeyMapper.getControlCode(ch))
        scrollOffset = 0
    }

    fun sendKey(key: String) {
        session?.write(key.toByteArray(Charsets.UTF_8))
        scrollOffset = 0
    }
}
