package io.github.ddsimoes.autoswt

import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.*
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.widgets.*
import java.io.File
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.reflect.KClass
import kotlin.test.assertTrue


class AutoSWT private constructor() {

    companion object {
        private const val DONT_CLOSE_KEY = "AutoSWT.dontClose"

        private val initialized = AtomicBoolean(false)
        private lateinit var display: Display

        private var asyncLoop = false
        private val executor by lazy {
            Executors.newSingleThreadExecutor {
                Thread(it, "AutoSWT-main")
            }
        }
        private val runLoop = AtomicBoolean(true)
        private val lastEventTime = AtomicLong(0)

        fun setup(display: Display) {
            check(!initialized.getAndSet(true)) { "SWTAuto is already initialized." }
            this.display = display
        }

        private fun testSetup() {
            if (!initialized.getAndSet(true)) {
                initAsync()
            }
        }

        private fun initAsync() {
            val latch = CountDownLatch(1)
            executor.execute {
                try {
                    initTestDisplay()
                } finally {
                    latch.countDown()
                }
            }
            asyncLoop = true
            latch.await(1, TimeUnit.SECONDS)
        }

        private fun initTestDisplay() {
            display = TestDisplay()
            display.warnings = false
            display.addFilter(SWT.MouseDown) {
                if (it.stateMask and SWT.CONTROL != 0) {
                    val shell = (it.widget as Control).shell
                    if (shell.getData(DONT_CLOSE_KEY) == null) {
                        val latch = CountDownLatch(1)
                        shell.setData(DONT_CLOSE_KEY, latch)
                        shell.addListener(SWT.Close) {
                            latch.countDown()
                        }
                    }
                }
            }
        }

        private val running = AtomicBoolean(false)

        private fun runSWTAsyncLoop(stopOnIdle: Boolean) {
            check(asyncLoop) { "SWT loop only run for tests." }
            if (!running.getAndSet(true)) {
                executor.execute {
                    try {
                        runSWTLoop(stopOnIdle)
                    } finally {
                        running.set(false)
                    }
                }
            }
        }

        private val errors = ConcurrentLinkedQueue<Throwable>()

        private fun runSWTLoop(stopOnIdle: Boolean) {
            println("Starting SWT test main loop.")
            errors.clear()

            val display = display

            runLoop.set(!stopOnIdle)

            while (!display.isDisposed && runLoop.get()) {
                try {
                    if (!display.readAndDispatch()) {
                        display.sleep()
                    }
                } catch (e: Throwable) {
                    errors.add(e)
                    e.printStackTrace()
                }
            }

            val maxIdleTime = 500 //TODO make possible for tests to configure or pause idle checking
            val wakerTimeout = maxIdleTime + 10

            val waker = object : Runnable {
                override fun run() {
                    if (runLoop.get() && !display.isDisposed) {
                        display.timerExec(wakerTimeout, this)
                    }
                }
            }

            lastEventTime.set(System.currentTimeMillis())

            while (!display.isDisposed && runLoop.get()) {
                try {
                    if ((System.currentTimeMillis() - lastEventTime.get()) > maxIdleTime) {
                        return
                    }
                    if (!display.readAndDispatch()) {
                        lastEventTime.set(System.currentTimeMillis())
                        display.timerExec(wakerTimeout, waker)
                        display.sleep()
                        if ((System.currentTimeMillis() - lastEventTime.get()) > maxIdleTime) {
                            return
                        }
                    }
                    lastEventTime.set(System.currentTimeMillis())
                } catch (e: Throwable) {
                    errors.add(e)
                    e.printStackTrace()
                }
            }
        }

        fun create() = AutoSWT()
    }

    val display: Display
        get() = AutoSWT.display

    var afterActionDelay = 0L

    init {
        val m = findTestMethod()
        if (m != null) {
            println("AutoSWT.init: ${m.declaringClass.simpleName}.${m.name}")
        }

        testSetup()
    }

    fun testShell(width: Int = 400,  height: Int = 400, block: Shell.() -> Unit): Shell {
        val testMethod = findTestMethod()
        return runOnSWT {
            createTestShell(testMethod, width, height, block)
        }
    }

    private fun createTestShell(testMethod: Method?, width: Int, height: Int, block: Shell.() -> Unit): Shell {
        val shell = Shell(display, SWT.SHELL_TRIM or SWT.TOOL)
        if (testMethod != null) {
            shell.text = "${testMethod.declaringClass.simpleName}.${testMethod.name} [Test]"
        }
        shell.layout = FillLayout()
        shell.setSize(width, height)

        block(shell)

        shell.addListener(SWT.Close) {
            shell.dispose()
        }

        shell.isVisible = true

        return shell
    }

    private fun findTestMethod(): Method? {
        val stackTrace = Thread.currentThread().stackTrace

        val junitIdx = stackTrace.indexOfFirst { it.className.startsWith("org.junit") }
        if (junitIdx == -1) {
            return null
        }

        var i = junitIdx - 1
        while (i >= 0) {
            val element = stackTrace[i]
            val method = try {
                Class.forName(element.className).getMethod(element.methodName)
            } catch (_: NoSuchMethodException) {
                null
            }

            @Suppress("UNCHECKED_CAST")
            val testAnnotation = Class.forName("org.junit.jupiter.api.Test") as Class<Annotation>
            if (method != null && method.isAnnotationPresent(testAnnotation)) {
                return method
            }
            i--
        }

        return null
    }

    fun waitForIdle(timeout: Long = 2000, minIdleTime: Long = 200): Boolean {
        check(display.thread != Thread.currentThread()) { "Should be called outside of SWT thread." }

        val t0 = System.currentTimeMillis()
        while (true) {
            val idleTime = System.currentTimeMillis() - lastEventTime.get()
            if (idleTime >= minIdleTime) {
                return true
            }

            if (System.currentTimeMillis() - t0 >= timeout) {
                return false
            }
            Thread.sleep(20)
        }
    }

    inline fun <reified T: Control> Composite.find(recursive: Boolean = true, noinline matcher: (T) -> Boolean = { true }): T {
        return find(T::class, recursive, matcher)
    }

    inline fun <reified T: Control> Composite.findAll(recursive: Boolean = true, noinline matcher: (T) -> Boolean = { true }): List<T> {
        return findAll(T::class, recursive, matcher)
    }

    @PublishedApi
    internal fun <T: Control> Composite.find(kClass: KClass<T>, recursive: Boolean, matcher: (T) -> Boolean): T {
        return runOnSWT {
            val descendants = if (recursive) {
                descendants()
            } else {
                children.asSequence()
            }
            descendants.filterIsInstance(kClass.java).find(matcher) ?: throw NoSuchElementException()
        }
    }

    @PublishedApi
    internal fun <T: Control> Composite.findAll(kClass: KClass<T>, recursive: Boolean, matcher: (T) -> Boolean): List<T> {
        return runOnSWT {
            val descendants = if (recursive) {
                descendants()
            } else {
                children.asSequence()
            }
            descendants.filterIsInstance(kClass.java).filter(matcher).toList()
        }
    }

    private inline fun runAction(crossinline block: () -> Unit) {
        runOnSWT {
            block()
        }
        delayAfterAction()
    }


    fun <R> runOnSWT(flushEvents: Boolean = false, block: () -> R): R {
        if (Thread.currentThread() == display.thread) {
            return block()
        }
        val throwable = AtomicReference<Throwable>(null)
        val result = AtomicReference<R>(null)
        val latch = CountDownLatch(1)

        val runnable = Runnable {
            try {
                result.set(block())
            } catch (t: Throwable) {
                throwable.set(t)
            } finally {
                latch.countDown()
            }
        }

        val runDirect = !running.get()
        if (runDirect) {
            executor.execute(runnable)
        } else {
            display.asyncExec(runnable)
            //maybe it stopped: force flushing
            if (!running.get()) {
                runSWTAsyncLoop(true)
            }
        }

        if (!runDirect) {
            while (true) {
                try {
                    latch.await(1, TimeUnit.SECONDS)
                    break
                } catch (_: InterruptedException) {
                    runSWTAsyncLoop(true)
                }
            }
        } else {
            latch.await()
        }

        if (runDirect && flushEvents) {
            runSWTAsyncLoop(true)
        }

        val t = throwable.get()
        if (t != null) {
            throw RuntimeException("runOnSWT {} block exception", t)
        }
        return result.get()
    }

    fun Composite.descendants(): Sequence<Control> {
        return iterator {
            yieldAll(children.iterator())
            children.forEach { child ->
                if (child is Composite) {
                    yieldAll(child.descendants())
                }
            }
        }.asSequence()
    }

    fun Button.doSelect() {
        runAction {
            val button = this
            if (button.style hasAny (SWT.CHECK or SWT.RADIO or SWT.TOGGLE)) {
                if (!button.selection) {
                    button.selection = true
                    button.notifyListeners(SWT.Selection, Event().apply {
                        widget = button
                        selection = true
                    })
                }
            } else {
                button.notifyListeners(SWT.Selection, Event().apply {
                    widget = button
                })
            }
        }
    }


    private fun delayAfterAction() {
        if (afterActionDelay > 0) {
            Thread.sleep(afterActionDelay)
        }
    }

    fun Button.doDeselect() {
        runAction {
            val button = this
            if (button.style hasAny (SWT.CHECK or SWT.RADIO or SWT.TOGGLE)) {
                if (button.selection) {
                    button.selection = false
                    button.notifyListeners(SWT.Selection, Event().apply {
                        widget = button
                        selection = false
                    })
                }
            }
        }
    }

    infix fun Int.hasAny(n: Int): Boolean {
        return this and n != 0
    }

    operator fun Int.contains(flag: Int) = (this and flag) == flag

    // ========== KEYBOARD INTERACTIONS ==========

    fun Text.typeText(text: String) {
        runAction {
            this.text = text
            // Simulate typing events
            text.forEach { char ->
                val event = Event().apply {
                    widget = this@typeText
                    character = char
                    keyCode = char.code
                }
                notifyListeners(SWT.KeyDown, event)
                notifyListeners(SWT.KeyUp, event)
            }
            // Trigger modify event
            notifyListeners(SWT.Modify, Event().apply { widget = this@typeText })
        }
    }

    fun Text.clearText() {
        runAction {
            this.text = ""
            notifyListeners(SWT.Modify, Event().apply { widget = this@clearText })
        }
    }

    fun Control.pressKey(keyCode: Int, modifiers: Int = 0) {
        runAction {
            val event = Event().apply {
                widget = this@pressKey
                this.keyCode = keyCode
                stateMask = modifiers
                character = when (keyCode) {
                    13 -> '\r' // CR
                    9 -> '\t'  // TAB
                    27 -> 0x1B.toChar() // ESC
                    else -> 0.toChar()
                }
            }
            notifyListeners(SWT.KeyDown, event)
            notifyListeners(SWT.KeyUp, event)
        }
    }



    fun Control.pressEnter() = pressKey(13) // CR
    fun Control.pressTab() = pressKey(9)   // TAB
    fun Control.pressEscape() = pressKey(27) // ESC

    // ========== MOUSE INTERACTIONS ==========

    fun Control.click(x: Int = -1, y: Int = -1) {
        runAction {
            val bounds = this.bounds
            val clickX = if (x == -1) bounds.width / 2 else x
            val clickY = if (y == -1) bounds.height / 2 else y
            
            val event = Event().apply {
                widget = this@click
                button = 1 // Left button
                this.x = clickX
                this.y = clickY
                count = 1
            }
            
            notifyListeners(SWT.MouseDown, event)
            notifyListeners(SWT.MouseUp, event)
            
            // For buttons, also trigger selection
            if (this is Button) {
                doSelect()
            }
        }
    }

    fun Control.doubleClick(x: Int = -1, y: Int = -1) {
        runAction {
            val bounds = this.bounds
            val clickX = if (x == -1) bounds.width / 2 else x
            val clickY = if (y == -1) bounds.height / 2 else y
            
            val event = Event().apply {
                widget = this@doubleClick
                button = 1
                this.x = clickX
                this.y = clickY
                count = 2
            }
            
            notifyListeners(SWT.MouseDoubleClick, event)
        }
    }

    fun Control.rightClick(x: Int = -1, y: Int = -1) {
        runAction {
            val bounds = this.bounds
            val clickX = if (x == -1) bounds.width / 2 else x
            val clickY = if (y == -1) bounds.height / 2 else y
            
            val event = Event().apply {
                widget = this@rightClick
                button = 3 // Right button
                this.x = clickX
                this.y = clickY
                count = 1
            }
            
            notifyListeners(SWT.MouseDown, event)
            notifyListeners(SWT.MouseUp, event)
        }
    }

    fun Control.hover(x: Int = -1, y: Int = -1) {
        runAction {
            val bounds = this.bounds
            val hoverX = if (x == -1) bounds.width / 2 else x
            val hoverY = if (y == -1) bounds.height / 2 else y
            
            val event = Event().apply {
                widget = this@hover
                this.x = hoverX
                this.y = hoverY
            }
            
            notifyListeners(SWT.MouseEnter, event)
            notifyListeners(SWT.MouseMove, event)
        }
    }

    fun Control.dragTo(target: Control, targetX: Int = -1, targetY: Int = -1) {
        runAction {
            val sourceBounds = this.bounds
            val targetBounds = target.bounds
            
            val startX = sourceBounds.width / 2
            val startY = sourceBounds.height / 2
            val endX = if (targetX == -1) targetBounds.width / 2 else targetX
            val endY = if (targetY == -1) targetBounds.height / 2 else targetY
            
            // Start drag
            val startEvent = Event().apply {
                widget = this@dragTo
                button = 1
                x = startX
                y = startY
            }
            notifyListeners(SWT.MouseDown, startEvent)
            notifyListeners(SWT.DragDetect, startEvent)
            
            // End drag
            val endEvent = Event().apply {
                widget = target
                button = 1
                x = endX
                y = endY
            }
            target.notifyListeners(SWT.MouseUp, endEvent)
        }
    }

    // ========== ADVANCED WAITERS & CHECKERS ==========

    fun waitUntil(timeout: Long = 5000, condition: () -> Boolean): Boolean {
        check(display.thread != Thread.currentThread()) { "Should be called outside of SWT thread." }
        
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeout) {
            val result = runOnSWT { condition() }
            if (result) return true
            Thread.sleep(50)
        }
        return false
    }

    fun waitForText(control: Control, expectedText: String, timeout: Long = 5000): Boolean {
        return waitUntil(timeout) {
            when (control) {
                is Text -> control.text == expectedText
                is Button -> control.text == expectedText
                else -> control.toString().contains(expectedText)
            }
        }
    }

    fun waitForEnabled(control: Control, timeout: Long = 5000): Boolean {
        return waitUntil(timeout) { control.isEnabled }
    }

    fun waitForVisible(control: Control, timeout: Long = 5000): Boolean {
        return waitUntil(timeout) { control.isVisible }
    }

    // ========== STATE CHECKERS ==========

    fun Control.isDisplayed(): Boolean {
        return runOnSWT {!isDisposed &&  isVisible }
    }

    fun Control.visibleBounds(relativeTo: Control = runOnSWT { shell }): Rectangle {
        return runOnSWT {
            computeVisibleBounds(this, relativeTo)
        }
    }

    private fun computeVisibleBounds(control: Control, relativeTo: Control): Rectangle {
        val bounds = control.getAbsoluteBounds()

        if (!control.visible) {
            bounds.width = 0
            bounds.height = 0
            return bounds
        }

        val parent = control.parent
        if (parent == null || parent == relativeTo) {
            return bounds
        }

        val parentBounds = computeVisibleBounds(parent, relativeTo) ?: return bounds

        val clippedBounds = parentBounds.intersection(bounds)
        if (clippedBounds.width < 1 || clippedBounds.height < 1) {
            println(this::class.simpleName + " [bounds] local: $bounds; parent: $parentBounds; visible: $clippedBounds")
        }

        return clippedBounds
    }

    fun Control.getAbsoluteBounds(): Rectangle {
        return runOnSWT {
            val bounds = this.bounds
            val location = toDisplay(0, 0)
            Rectangle(location.x, location.y, bounds.width, bounds.height)
        }
    }

    // ========== LAYOUT ASSERTION API ==========

    /**
     * Layout assertion API for testing UI component positioning and sizing.
     * Provides fluent methods for common layout testing scenarios.
     * 
     * ## Usage Examples
     * 
     * ```kotlin
     * // Single control assertions
     * button.assertLayout()
     *   .isVisible()
     *   .hasMinSize(width = 100, height = 30)
     *   .isRightOf(label, gap = 8)
     *   .isWithin(container)
     * 
     * // Multiple control assertions
     * listOf(button1, button2, button3).assertLayout()
     *   .areAllVisible()
     *   .areArrangedInRow(minGap = 8)
     *   .areRightAlignedWith()
     *   .fitWithin(toolbar)
     * ```
     * 
     * ## Tolerance Philosophy
     * 
     * Most layout assertions use exact matching (tolerance = 0) by default to catch real layout bugs.
     * Only use tolerance when genuinely needed for platform differences or mathematical rounding:
     * 
     * - **Exact (tolerance = 0)**: positioning, alignment, filling, spacing
     * - **Minimal (tolerance = 1)**: center calculations that involve division
     * - **Contextual**: edge positioning uses maxDistance, centering uses larger tolerance
     * 
     * ## Method Categories
     * 
     * ### Visibility & Sizing
     * - `isVisible()`, `isInvisible()` - Check if control has non-zero dimensions
     * - `hasMinSize()`, `hasMaxSize()`, `hasSize()` - Dimension constraints
     * 
     * ### Positioning
     * - `isAt(x, y)` - Exact coordinate positioning
     * - `isLeftOf()`, `isRightOf()`, `isAbove()`, `isBelow()` - Relative positioning with optional gaps
     * - `isWithin(container)` - Containment within parent bounds
     * 
     * ### Alignment
     * - `isLeftAlignedWith()`, `isRightAlignedWith()` - Edge alignment
     * - `isTopAlignedWith()`, `isBottomAlignedWith()` - Edge alignment
     * - `isCenterAlignedHorizontallyWith()`, `isCenterAlignedVerticallyWith()` - Center alignment
     * 
     * ### Layout Patterns
     * - `isCenteredIn(container)` - Center positioning within container
     * - `fillsHorizontally()`, `fillsVertically()` - Full-width/height filling
     * - `isNearLeftEdgeOf()`, `isNearRightEdgeOf()` etc. - Proximity to edges
     * - `doesNotOverlap(other)` - Non-overlapping assertion
     * 
     * ### Bulk Operations (for List<Control>)
     * - `areAllVisible()` - All controls visible
     * - `areArrangedInRow()`, `areArrangedInColumn()` - Sequential layout
     * - `areHorizontallyAligned()`, `areVerticallyAligned()` - Alignment consistency
     * - `haveEqualSpacing()` - Consistent gaps between controls
     * - `fitWithin(container)`, `doNotOverlap()` - Containment and overlap checks
     * 
     * ## Error Messages
     * 
     * All assertions provide detailed error messages with actual vs expected values:
     * ```
     * Control should be right-aligned (±0): this=Rectangle{x=100, y=20, width=80, height=25} other=Rectangle{x=200, y=20, width=80, height=25}
     * ```
     */
    inner class LayoutAssertion(private val control: Control, private val relativeTo: Control? = null) {
        private val bounds: Rectangle by lazy { 
            if (relativeTo != null) control.visibleBounds(relativeTo) else control.visibleBounds()
        }
        
        /**
         * Assert the control is visible (has non-zero width and height)
         */
        fun isVisible(message: String? = null): LayoutAssertion {
            val msg = message ?: "Control should be visible: $bounds"
            assertTrue(bounds.width > 0 && bounds.height > 0, msg)
            return this
        }
        
        /**
         * Assert the control is invisible (has zero width or height)
         */
        fun isInvisible(message: String? = null): LayoutAssertion {
            val msg = message ?: "Control should be invisible: $bounds"
            assertTrue(bounds.width <= 0 || bounds.height <= 0, msg)
            return this
        }
        
        /**
         * Assert the control has minimum dimensions
         */
        fun hasMinSize(width: Int = 0, height: Int = 0, message: String? = null): LayoutAssertion {
            val msg = message ?: "Control should have min size ${width}x${height}: actual $bounds"
            assertTrue(bounds.width >= width, "$msg (width: ${bounds.width} < $width)")
            assertTrue(bounds.height >= height, "$msg (height: ${bounds.height} < $height)")
            return this
        }
        
        /**
         * Assert the control has maximum dimensions
         */
        fun hasMaxSize(width: Int = Int.MAX_VALUE, height: Int = Int.MAX_VALUE, message: String? = null): LayoutAssertion {
            val msg = message ?: "Control should have max size ${width}x${height}: actual $bounds"
            assertTrue(bounds.width <= width, "$msg (width: ${bounds.width} > $width)")
            assertTrue(bounds.height <= height, "$msg (height: ${bounds.height} > $height)")
            return this
        }
        
        /**
         * Assert the control has exact dimensions
         */
        fun hasSize(width: Int, height: Int, tolerance: Int = 0, message: String? = null): LayoutAssertion {
            val msg = message ?: "Control should have size ${width}x${height} (±$tolerance): actual $bounds"
            assertTrue(
                abs(bounds.width - width) <= tolerance,
                "$msg (width: ${bounds.width} vs $width)")
            assertTrue(
                abs(bounds.height - height) <= tolerance,
                "$msg (height: ${bounds.height} vs $height)")
            return this
        }
        
        /**
         * Assert the control is positioned within the given container
         */
        fun isWithin(container: Control, message: String? = null): LayoutAssertion {
            val containerBounds = container.visibleBounds(relativeTo ?: runOnSWT { control.shell })
            val msg = message ?: "Control should be within container: control=$bounds container=$containerBounds"
            assertTrue(bounds.x >= containerBounds.x, "$msg (left edge)")
            assertTrue(bounds.y >= containerBounds.y, "$msg (top edge)")
            assertTrue(bounds.x + bounds.width <= containerBounds.x + containerBounds.width, "$msg (right edge)")
            assertTrue(bounds.y + bounds.height <= containerBounds.y + containerBounds.height, "$msg (bottom edge)")
            return this
        }
        
        /**
         * Assert the control is positioned to the left of another control
         */
        fun isLeftOf(other: Control, gap: Int = 0, tolerance: Int = 0, message: String? = null): LayoutAssertion {
            val otherBounds = other.visibleBounds(relativeTo ?: runOnSWT { control.shell })
            val actualGap = otherBounds.x - (bounds.x + bounds.width)
            val msg = message ?: "Control should be left of other (gap≥$gap±$tolerance): this=$bounds other=$otherBounds actualGap=$actualGap"
            assertTrue(actualGap >= gap - tolerance, msg)
            return this
        }
        
        /**
         * Assert the control is positioned to the right of another control
         */
        fun isRightOf(other: Control, gap: Int = 0, tolerance: Int = 0, message: String? = null): LayoutAssertion {
            val otherBounds = other.visibleBounds(relativeTo ?: runOnSWT { control.shell })
            val actualGap = bounds.x - (otherBounds.x + otherBounds.width)
            val msg = message ?: "Control should be right of other (gap≥$gap±$tolerance): this=$bounds other=$otherBounds actualGap=$actualGap"
            assertTrue(actualGap >= gap - tolerance, msg)
            return this
        }
        
        /**
         * Assert the control is positioned above another control
         */
        fun isAbove(other: Control, gap: Int = 0, tolerance: Int = 0, message: String? = null): LayoutAssertion {
            val otherBounds = other.visibleBounds(relativeTo ?: runOnSWT { control.shell })
            val actualGap = otherBounds.y - (bounds.y + bounds.height)
            val msg = message ?: "Control should be above other (gap≥$gap±$tolerance): this=$bounds other=$otherBounds actualGap=$actualGap"
            assertTrue(actualGap >= gap - tolerance, msg)
            return this
        }
        
        /**
         * Assert the control is positioned below another control
         */
        fun isBelow(other: Control, gap: Int = 0, tolerance: Int = 0, message: String? = null): LayoutAssertion {
            val otherBounds = other.visibleBounds(relativeTo ?: runOnSWT { control.shell })
            val actualGap = bounds.y - (otherBounds.y + otherBounds.height)
            val msg = message ?: "Control should be below other (gap≥$gap±$tolerance): this=$bounds other=$otherBounds actualGap=$actualGap"
            assertTrue(actualGap >= gap - tolerance, msg)
            return this
        }
        
        /**
         * Assert the control does not overlap with another control
         */
        fun doesNotOverlap(other: Control, message: String? = null): LayoutAssertion {
            val otherBounds = other.visibleBounds(relativeTo ?: runOnSWT { control.shell })
            val msg = message ?: "Controls should not overlap: this=$bounds other=$otherBounds"
            val noOverlap = (bounds.x + bounds.width <= otherBounds.x) || 
                           (otherBounds.x + otherBounds.width <= bounds.x) ||
                           (bounds.y + bounds.height <= otherBounds.y) ||
                           (otherBounds.y + otherBounds.height <= bounds.y)
            assertTrue(noOverlap, msg)
            return this
        }
        
        /**
         * Assert controls are aligned on their left edges
         */
        fun isLeftAlignedWith(other: Control, tolerance: Int = 0, message: String? = null): LayoutAssertion {
            val otherBounds = other.visibleBounds(relativeTo ?: runOnSWT { control.shell })
            val msg = message ?: "Controls should be left-aligned (±$tolerance): this=$bounds other=$otherBounds"
            assertTrue(abs(bounds.x - otherBounds.x) <= tolerance, msg)
            return this
        }
        
        /**
         * Assert controls are aligned on their right edges
         */
        fun isRightAlignedWith(other: Control, tolerance: Int = 0, message: String? = null): LayoutAssertion {
            val otherBounds = other.visibleBounds(relativeTo ?: runOnSWT { control.shell })
            val thisRight = bounds.x + bounds.width
            val otherRight = otherBounds.x + otherBounds.width
            val msg = message ?: "Controls should be right-aligned (±$tolerance): this=$bounds other=$otherBounds"
            assertTrue(abs(thisRight - otherRight) <= tolerance, msg)
            return this
        }
        
        /**
         * Assert controls are aligned on their top edges
         */
        fun isTopAlignedWith(other: Control, tolerance: Int = 0, message: String? = null): LayoutAssertion {
            val otherBounds = other.visibleBounds(relativeTo ?: runOnSWT { control.shell })
            val msg = message ?: "Controls should be top-aligned (±$tolerance): this=$bounds other=$otherBounds"
            assertTrue(abs(bounds.y - otherBounds.y) <= tolerance, msg)
            return this
        }
        
        /**
         * Assert controls are aligned on their bottom edges
         */
        fun isBottomAlignedWith(other: Control, tolerance: Int = 0, message: String? = null): LayoutAssertion {
            val otherBounds = other.visibleBounds(relativeTo ?: runOnSWT { control.shell })
            val thisBottom = bounds.y + bounds.height
            val otherBottom = otherBounds.y + otherBounds.height
            val msg = message ?: "Controls should be bottom-aligned (±$tolerance): this=$bounds other=$otherBounds"
            assertTrue(abs(thisBottom - otherBottom) <= tolerance, msg)
            return this
        }
        
        /**
         * Assert controls are center-aligned horizontally
         */
        fun isCenterAlignedHorizontallyWith(other: Control, tolerance: Int = 1, message: String? = null): LayoutAssertion {
            val otherBounds = other.visibleBounds(relativeTo ?: runOnSWT { control.shell })
            val thisCenter = bounds.x + bounds.width / 2
            val otherCenter = otherBounds.x + otherBounds.width / 2
            val msg = message ?: "Controls should be center-aligned horizontally (±$tolerance): this=$bounds other=$otherBounds"
            assertTrue(abs(thisCenter - otherCenter) <= tolerance, msg)
            return this
        }
        
        /**
         * Assert controls are center-aligned vertically
         */
        fun isCenterAlignedVerticallyWith(other: Control, tolerance: Int = 1, message: String? = null): LayoutAssertion {
            val otherBounds = other.visibleBounds(relativeTo ?: runOnSWT { control.shell })
            val thisCenter = bounds.y + bounds.height / 2
            val otherCenter = otherBounds.y + otherBounds.height / 2
            val msg = message ?: "Controls should be center-aligned vertically (±$tolerance): this=$bounds other=$otherBounds"
            assertTrue(abs(thisCenter - otherCenter) <= tolerance, msg)
            return this
        }
        
        /**
         * Assert the control is positioned at a specific location
         */
        fun isAt(x: Int, y: Int, tolerance: Int = 0, message: String? = null): LayoutAssertion {
            val msg = message ?: "Control should be at ($x, $y) ±$tolerance: actual $bounds"
            assertTrue(abs(bounds.x - x) <= tolerance, "$msg (x: ${bounds.x} vs $x)")
            assertTrue(abs(bounds.y - y) <= tolerance, "$msg (y: ${bounds.y} vs $y)")
            return this
        }
        
        /**
         * Assert the control is centered within its container
         */
        fun isCenteredIn(container: Control, tolerance: Int = 5, message: String? = null): LayoutAssertion {
            val containerBounds = container.visibleBounds(relativeTo ?: runOnSWT { control.shell })
            val expectedCenterX = containerBounds.x + containerBounds.width / 2
            val expectedCenterY = containerBounds.y + containerBounds.height / 2
            val actualCenterX = bounds.x + bounds.width / 2
            val actualCenterY = bounds.y + bounds.height / 2
            val msg = message ?: "Control should be centered in container (±$tolerance): control=$bounds container=$containerBounds"
            assertTrue(abs(actualCenterX - expectedCenterX) <= tolerance, "$msg (x-center)")
            assertTrue(abs(actualCenterY - expectedCenterY) <= tolerance, "$msg (y-center)")
            return this
        }
        
        /**
         * Assert the control fills the container horizontally
         */
        fun fillsHorizontally(container: Control, tolerance: Int = 0, message: String? = null): LayoutAssertion {
            val containerBounds = container.visibleBounds(relativeTo ?: runOnSWT { control.shell })
            val msg = message ?: "Control should fill container horizontally (±$tolerance): control=$bounds container=$containerBounds"
            assertTrue(abs(bounds.x - containerBounds.x) <= tolerance, "$msg (left edge)")
            assertTrue(abs((bounds.x + bounds.width) - (containerBounds.x + containerBounds.width)) <= tolerance, "$msg (right edge)")
            return this
        }
        
        /**
         * Assert the control fills the container vertically
         */
        fun fillsVertically(container: Control, tolerance: Int = 0, message: String? = null): LayoutAssertion {
            val containerBounds = container.visibleBounds(relativeTo ?: runOnSWT { control.shell })
            val msg = message ?: "Control should fill container vertically (±$tolerance): control=$bounds container=$containerBounds"
            assertTrue(abs(bounds.y - containerBounds.y) <= tolerance, "$msg (top edge)")
            assertTrue(abs((bounds.y + bounds.height) - (containerBounds.y + containerBounds.height)) <= tolerance, "$msg (bottom edge)")
            return this
        }
        
        /**
         * Assert the control is positioned near a specific edge of the container
         */
        fun isNearLeftEdgeOf(container: Control, maxDistance: Int = 10, message: String? = null): LayoutAssertion {
            val containerBounds = container.visibleBounds(relativeTo ?: runOnSWT { control.shell })
            val distance = bounds.x - containerBounds.x
            val msg = message ?: "Control should be near left edge (≤${maxDistance}px): control=$bounds container=$containerBounds distance=$distance"
            assertTrue(distance >= 0 && distance <= maxDistance, msg)
            return this
        }
        
        /**
         * Assert the control is positioned near the right edge of the container
         */
        fun isNearRightEdgeOf(container: Control, maxDistance: Int = 10, message: String? = null): LayoutAssertion {
            val containerBounds = container.visibleBounds(relativeTo ?: runOnSWT { control.shell })
            val distance = (containerBounds.x + containerBounds.width) - (bounds.x + bounds.width)
            val msg = message ?: "Control should be near right edge (≤${maxDistance}px): control=$bounds container=$containerBounds distance=$distance"
            assertTrue(distance >= 0 && distance <= maxDistance, msg)
            return this
        }
        
        /**
         * Assert the control is positioned near the top edge of the container
         */
        fun isNearTopEdgeOf(container: Control, maxDistance: Int = 10, message: String? = null): LayoutAssertion {
            val containerBounds = container.visibleBounds(relativeTo ?: runOnSWT { control.shell })
            val distance = bounds.y - containerBounds.y
            val msg = message ?: "Control should be near top edge (≤${maxDistance}px): control=$bounds container=$containerBounds distance=$distance"
            assertTrue(distance >= 0 && distance <= maxDistance, msg)
            return this
        }
        
        /**
         * Assert the control is positioned near the bottom edge of the container
         */
        fun isNearBottomEdgeOf(container: Control, maxDistance: Int = 10, message: String? = null): LayoutAssertion {
            val containerBounds = container.visibleBounds(relativeTo ?: runOnSWT { control.shell })
            val distance = (containerBounds.y + containerBounds.height) - (bounds.y + bounds.height)
            val msg = message ?: "Control should be near bottom edge (≤${maxDistance}px): control=$bounds container=$containerBounds distance=$distance"
            assertTrue(distance >= 0 && distance <= maxDistance, msg)
            return this
        }
    }

    /**
     * Create a layout assertion for this control
     */
    fun Control.assertLayout(relativeTo: Control? = null): LayoutAssertion {
        return LayoutAssertion(this, relativeTo)
    }

    /**
     * Bulk assertion helper for multiple controls
     */
    inner class MultipleLayoutAssertion(private val controls: List<Control>, private val relativeTo: Control? = null) {
        
        /**
         * Assert all controls are visible
         */
        fun areAllVisible(message: String? = null): MultipleLayoutAssertion {
            controls.forEachIndexed { index, control ->
                val msg = message ?: "Control[$index] should be visible"
                control.assertLayout(relativeTo).isVisible(msg)
            }
            return this
        }
        
        /**
         * Assert all controls are horizontally aligned (same Y position)
         */
        fun areHorizontallyAligned(tolerance: Int = 0, message: String? = null): MultipleLayoutAssertion {
            if (controls.size < 2) return this
            val firstBounds = controls.first().visibleBounds(relativeTo ?: runOnSWT { controls.first().shell })
            controls.drop(1).forEachIndexed { index, control ->
                val msg = message ?: "Control[${index + 1}] should be horizontally aligned with Control[0]"
                control.assertLayout(relativeTo).isTopAlignedWith(controls.first(), tolerance, msg)
            }
            return this
        }
        
        /**
         * Assert all controls are vertically aligned (same X position) 
         */
        fun areVerticallyAligned(tolerance: Int = 0, message: String? = null): MultipleLayoutAssertion {
            if (controls.size < 2) return this
            controls.drop(1).forEachIndexed { index, control ->
                val msg = message ?: "Control[${index + 1}] should be vertically aligned with Control[0]"
                control.assertLayout(relativeTo).isLeftAlignedWith(controls.first(), tolerance, msg)
            }
            return this
        }
        
        /**
         * Assert all controls are right-aligned with each other
         */
        fun areRightAlignedWith(tolerance: Int = 0, message: String? = null): MultipleLayoutAssertion {
            if (controls.size < 2) return this
            controls.drop(1).forEachIndexed { index, control ->
                val msg = message ?: "Control[${index + 1}] should be right-aligned with Control[0]"
                control.assertLayout(relativeTo).isRightAlignedWith(controls.first(), tolerance, msg)
            }
            return this
        }
        
        /**
         * Assert controls are arranged in a row (increasing X positions)
         */
        fun areArrangedInRow(minGap: Int = 0, message: String? = null): MultipleLayoutAssertion {
            if (controls.size < 2) return this
            controls.zipWithNext().forEachIndexed { index, (first, second) ->
                val msg = message ?: "Control[${index + 1}] should be to the right of Control[$index] with gap≥$minGap"
                second.assertLayout(relativeTo).isRightOf(first, minGap, message = msg)
            }
            return this
        }
        
        /**
         * Assert controls are arranged in a column (increasing Y positions)
         */
        fun areArrangedInColumn(minGap: Int = 0, message: String? = null): MultipleLayoutAssertion {
            if (controls.size < 2) return this
            controls.zipWithNext().forEachIndexed { index, (first, second) ->
                val msg = message ?: "Control[${index + 1}] should be below Control[$index] with gap≥$minGap"
                second.assertLayout(relativeTo).isBelow(first, minGap, message = msg)
            }
            return this
        }
        
        /**
         * Assert all controls have equal spacing between them
         */
        fun haveEqualSpacing(expectedGap: Int, tolerance: Int = 0, horizontal: Boolean = true, message: String? = null): MultipleLayoutAssertion {
            if (controls.size < 2) return this
            controls.zipWithNext().forEachIndexed { index, (first, second) ->
                val firstBounds = first.visibleBounds(relativeTo ?: runOnSWT { first.shell })
                val secondBounds = second.visibleBounds(relativeTo ?: runOnSWT { second.shell })
                
                val actualGap = if (horizontal) {
                    secondBounds.x - (firstBounds.x + firstBounds.width)
                } else {
                    secondBounds.y - (firstBounds.y + firstBounds.height)
                }
                
                val msg = message ?: "Gap between Control[$index] and Control[${index + 1}] should be $expectedGap±$tolerance, actual: $actualGap"
                assertTrue(abs(actualGap - expectedGap) <= tolerance, msg)
            }
            return this
        }
        
        /**
         * Assert all controls do not overlap with each other
         */
        fun doNotOverlap(message: String? = null): MultipleLayoutAssertion {
            for (i in controls.indices) {
                for (j in i + 1 until controls.size) {
                    val msg = message ?: "Control[$i] should not overlap with Control[$j]"
                    controls[i].assertLayout(relativeTo).doesNotOverlap(controls[j], msg)
                }
            }
            return this
        }
        
        /**
         * Assert all controls fit within a container
         */
        fun fitWithin(container: Control, message: String? = null): MultipleLayoutAssertion {
            controls.forEachIndexed { index, control ->
                val msg = message ?: "Control[$index] should fit within container"
                control.assertLayout(relativeTo).isWithin(container, msg)
            }
            return this
        }
    }

    /**
     * Create a multiple layout assertion for a list of controls
     */
    fun List<Control>.assertLayout(relativeTo: Control? = null): MultipleLayoutAssertion {
        return MultipleLayoutAssertion(this, relativeTo)
    }

    fun Control.takeScreenshot(): Image {
        return runOnSWT {
            val control = if (this is Shell) {
                val children = this.children
                check(children.size == 1 && children.first() is Composite) { "Shells are only supported with a single composite" }
                children.first()
            } else {
                this
            }
            val bounds = control.getBounds()

            check (!bounds.isEmpty) { "Can't take snapshot of an empty sized control" }

            val image = Image(control.getDisplay(), bounds.width, bounds.height)

            val gc = GC(image)
            check(control.print(gc)) { "Screenshot not supported" }
            gc.dispose()

            image
        }
    }

    fun Composite.saveSVG(file: File) {
        val composite = this
        runOnSWT {
            SWTSvgGenerator().generateToFile(composite, file.absolutePath)
        }
    }

    fun Composite.saveSVG(suffix: String? = null) {
        val file = screenshotFile(suffix, "svg")
        saveSVG(file)
    }

    private fun screenshotFile(suffix: String?, extension: String): File {
        val namePrefix = findTestMethod()?.let { it.declaringClass.simpleName + "." + it.name }
            ?: "test-${System.currentTimeMillis()}"
        val nameSuffix = suffix?.let { "-$it" } ?: ""
        val file = File(screenshotDir(), "$namePrefix$nameSuffix.$extension")
        return file
    }

    private fun screenshotDir(): File = File("./build/test-screenshots/").apply {
        mkdirs()
    }

    fun Image.saveTo(file: File) {
        check(file.extension == "png") { "Only PNG supported" }
        val loader = ImageLoader()
        loader.data = arrayOf<ImageData>(this.imageData)
        loader.save(file.absolutePath, SWT.IMAGE_PNG)
    }

    fun Control.saveScreenshot(suffix: String? = null) {
        takeScreenshot().use {
            it.saveTo(screenshotFile(suffix, "png"))
        }
    }

    fun <T: Widget> T.test(block: (T) -> Unit) {
        val w = this
        runTest {
            block(w)
        }
    }

    fun <T: Resource, R> T.use(block: (T) -> R): R {
        val result = try {
            block(this)
        } finally {
            runOnSWT {
                dispose()
            }
        }
        return result
    }

    fun runTest(block: () -> Unit) {
        check(Thread.currentThread() != display.thread) { "Tests should run outside of SWT thread." }

        val latch = CountDownLatch(1)

        display.asyncExec {
            latch.countDown()
        }

        runSWTAsyncLoop(false)

        try {
            block()
        } finally {
            runLoop.set(false)
            display.wake()
        }

        while (running.get()) {
            Thread.sleep(10)
        }

    }

    fun Control.string(): String {
        val control = this

        return runOnSWT {
            val info = mutableListOf<String>()

            info.add("${control.javaClass.simpleName}[${control.hashCode().toString(16)}]")

            when (control) {
                is Label -> {
                    info.add("text=\"${control.text}\"")
                    info.add("style=${getLabelStyle(control)}")
                }
                is Button -> {
                    info.add("text=\"${control.text}\"")
                    info.add("style=${getButtonStyle(control)}")
                    info.add("selection=${control.selection}")
                }
                is Text -> {
                    val text = if (control.text.length > 20)
                        "${control.text.take(20)}..."
                    else control.text
                    info.add("text=\"$text\"")
                    info.add("editable=${control.editable}")
                    info.add("chars=${control.textLimit}")
                }
                is org.eclipse.swt.widgets.List -> {
                    info.add("items=${control.itemCount}")
                    info.add("selection=${control.selectionIndex}")
                    if (control.itemCount > 0) {
                        info.add("firstItem=\"${control.getItem(0)}\"")
                    }
                }
                is Table -> {
                    info.add("items=${control.itemCount}")
                    info.add("columns=${control.columnCount}")
                    info.add("selection=${control.selectionIndex}")
                    info.add("headerVisible=${control.headerVisible}")
                    info.add("linesVisible=${control.linesVisible}")
                }
                is Tree -> {
                    info.add("items=${control.itemCount}")
                    info.add("selection=${control.selectionCount}")
                    info.add("headerVisible=${control.headerVisible}")
                    info.add("linesVisible=${control.linesVisible}")
                }
                is Combo -> {
                    info.add("items=${control.itemCount}")
                    info.add("text=\"${control.text}\"")
                    info.add("selection=${control.selectionIndex}")
                }
                is Shell -> {
                    info.add("title=\"${control.text}\"")
                    info.add("minimized=${control.minimized}")
                    info.add("maximized=${control.maximized}")
                    info.add("active=${control == control.display.activeShell}")
                }
                is Composite -> {
                    info.add("children=${control.children.size}")
                    if (control is Group) {
                        info.add("text=\"${control.text}\"")
                    }
                    if (control is TabFolder) {
                        info.add("items=${control.itemCount}")
                        info.add("selection=${control.selectionIndex}")
                    }
                }
                is ProgressBar -> {
                    info.add("min=${control.minimum}")
                    info.add("max=${control.maximum}")
                    info.add("value=${control.selection}")
                }
                is Scale -> {
                    info.add("min=${control.minimum}")
                    info.add("max=${control.maximum}")
                    info.add("value=${control.selection}")
                    info.add("increment=${control.increment}")
                    info.add("pageIncrement=${control.pageIncrement}")
                }
                is Slider -> {
                    info.add("min=${control.minimum}")
                    info.add("max=${control.maximum}")
                    info.add("value=${control.selection}")
                    info.add("thumb=${control.thumb}")
                }
            }

            // Font information
            control.font?.let { font ->
                val fontData = font.fontData.firstOrNull()
                fontData?.let {
                    info.add("font=\"${it.name}\" size=${it.height}")
                }
            }

            // Color information
            control.foreground?.let { color ->
                info.add("fg=rgb(${color.red},${color.green},${color.blue})")
            }
            control.background?.let { color ->
                info.add("bg=rgb(${color.red},${color.green},${color.blue})")
            }

            info.joinToString(" ")
        }

    }

    /**
     * Gets label style information.
     */
    private fun getLabelStyle(label: Label): String {
        val style = label.style
        val styles = mutableListOf<String>()

        if (style and SWT.CENTER != 0) styles.add("CENTER")
        if (style and SWT.LEFT != 0) styles.add("LEFT")
        if (style and SWT.RIGHT != 0) styles.add("RIGHT")
        if (style and SWT.WRAP != 0) styles.add("WRAP")
        if (style and SWT.SEPARATOR != 0) styles.add("SEPARATOR")

        return if (styles.isEmpty()) "NONE" else styles.joinToString("|")
    }

    /**
     * Gets button style information.
     */
    private fun getButtonStyle(button: Button): String {
        val style = button.style
        val styles = mutableListOf<String>()

        if (style and SWT.PUSH != 0) styles.add("PUSH")
        if (style and SWT.CHECK != 0) styles.add("CHECK")
        if (style and SWT.RADIO != 0) styles.add("RADIO")
        if (style and SWT.TOGGLE != 0) styles.add("TOGGLE")
        if (style and SWT.ARROW != 0) styles.add("ARROW")
        if (style and SWT.FLAT != 0) styles.add("FLAT")

        return if (styles.isEmpty()) "NONE" else styles.joinToString("|")
    }


    fun dispose() {
        val display = display
        if (display is TestDisplay) {
            val m = findTestMethod()
            runOnSWT(flushEvents = true) {
                if (m != null) {
                    println("AutoSWT.dispose: ${m.declaringClass.simpleName}.${m.name}")
                }
                display.reset()
                println("#diplay.shells: ${display.shells.size}")
            }
        }
    }

    fun Composite.refreshLayout(callback: (List<Rectangle>, List<Rectangle>) -> Unit) {
        val composite = this
        val (bounds1, bounds2) = runOnSWT {
            val b1 = composite.findAll<Control> { true }.let { children ->
                children.map { it.bounds }
            }

            composite.layout(true, true)

            val b2 = composite.findAll<Control> { true }.let { children ->
                children.map { it.bounds }
            }

            listOf(b1, b2)
        }

        callback(bounds1, bounds2)
    }


}

private class TestDisplay : Display() {
    private val dataKeys = mutableListOf<String>()

    override fun setData(key: String?, value: Any?) {
        super.setData(key, value)
        dataKeys.add(key!!)
    }

    fun reset() {
        shells.forEach {
            it.dispose()
        }

        dataKeys.forEach {
            super.setData(it, null)
        }

        dataKeys.clear()
    }

    override fun checkSubclass() {

    }
}


fun autoSWT(block: AutoSWT.() -> Unit) {
    synchronized(AutoSWT) {

        val auto = AutoSWT.create()

        try {
            auto.block()
        } finally {
            auto.dispose()
        }
    }
}
