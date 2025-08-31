# AutoSWT - SWT Testing/Automation Library

AutoSWT is a testing and automation library designed for testing SWT-based UIs. It provides thread-safe test automation, automatic display management, and debugging tools.

## Features

- **Widget Finding**: Find widgets by text, type, or custom predicates with built-in `runOnSWT` wrapping
- **Widget Interaction**: Click buttons, type text, clear fields with automatic event simulation
- **Thread-Safe Operations**: Handling of SWT's single-threaded nature
- **State Inspection**: Check widget visibility, enabled state, text content
- **Layout Assertion API**: Comprehensive fluent API for testing UI positioning, sizing, and alignment
- **Tree Inspection**: Debug widget hierarchy and structure with detailed logging
- **SVG Generation**: Create visual representations of SWT UIs with metadata
- **Automatic Cleanup**: Proper disposal of SWT resources and test isolation


## Basic Usage

### Test Structure

```kotlin
    @Test
fun testAdd() {
    autoSWT {
        testShell {
            SimpleList(this, SWT.NONE)
        }.test { shell ->
            val text = shell.find<Text>()
            val button = shell.find<Button>()
            val label = shell.find<Label> { it.text == "Number of items: 0" }

            runOnSWT {
                text.text = "Item 1"
            }

            button.doSelect()

            assertEquals("Number of items: 1", runOnSWT { label.text })
        }
    }
}
```

### Widget Finding

AutoSWT provides powerful widget finding with automatic thread safety:

```kotlin
// Find single widget (throws if not found)
val textField = shell.find<Text> { it.text.isEmpty() }
val button = shell.find<Button> { it.text == "Submit" }

// Find all matching widgets
val allButtons = shell.findAll<Button> { true }
val enabledButtons = shell.findAll<Button> { it.isEnabled }

// Recursive vs non-recursive searching
val directChildren = shell.findAll<Button>(recursive = false) { true }
val allDescendants = shell.findAll<Button>(recursive = true) { true }
```

### Widget Interactions

All interaction methods automatically wrap operations with `runOnSWT`:

```kotlin
// Text input
textField.typeText("Hello World")  // Types text and triggers events
textField.clearText()              // Clears text and triggers modify event

// Button operations  
button.doSelect()                  // Triggers selection event
button.doDeselect()               // For checkboxes/radio buttons

// Generic control operations
control.click()                   // Simulates mouse click
control.pressEnter()             // Simulates enter key
control.pressKey(SWT.ARROW_DOWN) // Custom key press
```

### Thread Safety Rules

**AutoSWT handles thread safety automatically in these cases:**
- All `find()` and `findAll()` operations
- All widget interaction methods (`typeText`, `doSelect`, etc.)
- Predicates inside `find()` calls

**You need `runOnSWT` for:**
- Direct widget property access outside of find predicates
- Multiple operations that must be atomic
- Widget creation and configuration

```kotlin
// AUTOMATIC thread safety - no runOnSWT needed
val buttons = shell.findAll<Button> { it.text.startsWith("Save") }
button.doSelect()

// MANUAL thread safety needed
val text = runOnSWT { label.text }
val results = runOnSWT { labels.map { it.text } }
```

## Layout Assertion API

AutoSWT provides a comprehensive layout assertion API for testing UI positioning, sizing, and alignment.

### Layout Assertions

```kotlin
label.assertLayout().isVisible().isLeftOf(button)
button.assertLayout().isTopAlignedWith(label)
```

### Single Control Assertions

Use `control.assertLayout()` to create assertions for individual widgets:

```kotlin
@Test
fun testButtonLayout() {
    autoSWT {
        testShell { shell ->
            // Setup UI with button
        }.test { shell ->
            val button = shell.find<Button> { it.text == "Submit" }
            val label = shell.find<Label> { it.text == "Name:" }
            val container = shell.find<Composite> { true }
            
            // Chain multiple layout assertions
            button.assertLayout()
                .isVisible()                           // Has non-zero dimensions
                .hasMinSize(width = 80, height = 25)   // Minimum size constraints
                .isRightOf(label, gap = 8)             // Positioned with gap
                .isWithin(container)                   // Contained within bounds
                .doesNotOverlap(label)                 // No overlap
        }
    }
}
```

### Multiple Control Assertions

Use `controls.assertLayout()` for bulk operations on lists of widgets:

```kotlin
@Test  
fun testToolbarLayout() {
    autoSWT {
        testShell { shell ->
            // Setup toolbar with multiple buttons
        }.test { shell ->
            val toolbar = shell.find<Composite> { /* find toolbar */ }
            val buttons = shell.findAll<Button> { true }
            
            // Bulk assertions on all buttons
            buttons.assertLayout()
                .areAllVisible()                       // All have non-zero dimensions
                .areArrangedInRow(minGap = 5)          // Sequential horizontal layout
                .areTopAlignedWith()                   // Same Y position
                .haveEqualSpacing(expectedGap = 10)    // Consistent gaps
                .fitWithin(toolbar)                    // All contained in toolbar
                .doNotOverlap()                        // No overlapping widgets
        }
    }
}
```

### Layout Assertion Categories

#### Visibility & Sizing
```kotlin
widget.assertLayout()
    .isVisible()                    // width > 0 && height > 0
    .isInvisible()                  // width <= 0 || height <= 0
    .hasMinSize(width = 100, height = 30)   // Minimum dimensions
    .hasMaxSize(width = 200, height = 50)   // Maximum dimensions  
    .hasSize(width = 150, height = 40)      // Exact dimensions
```

#### Positioning
```kotlin
widget.assertLayout()
    .isAt(x = 100, y = 50)          // Exact coordinates
    .isLeftOf(other, gap = 8)       // Positioned left with gap
    .isRightOf(other, gap = 8)      // Positioned right with gap
    .isAbove(other, gap = 5)        // Positioned above with gap
    .isBelow(other, gap = 5)        // Positioned below with gap
    .isWithin(container)            // Fully contained within bounds
```

#### Alignment  
```kotlin
widget.assertLayout()
    .isLeftAlignedWith(other)           // Same left edge (x coordinate)
    .isRightAlignedWith(other)          // Same right edge (x + width)
    .isTopAlignedWith(other)            // Same top edge (y coordinate)
    .isBottomAlignedWith(other)         // Same bottom edge (y + height)
    .isCenterAlignedHorizontallyWith(other)  // Same center X
    .isCenterAlignedVerticallyWith(other)    // Same center Y
```

#### Layout Patterns
```kotlin
widget.assertLayout()
    .isCenteredIn(container)                    // Centered within container
    .fillsHorizontally(container)               // Full width of container
    .fillsVertically(container)                 // Full height of container
    .isNearLeftEdgeOf(container, maxDistance = 5)   // Close to left edge
    .isNearRightEdgeOf(container, maxDistance = 5)  // Close to right edge
    .doesNotOverlap(other)                      // No overlap with other widget
```

#### Bulk Operations (for List<Control>)
```kotlin
widgets.assertLayout()
    .areAllVisible()                            // All widgets visible
    .areArrangedInRow(minGap = 0)              // Sequential horizontal layout
    .areArrangedInColumn(minGap = 0)           // Sequential vertical layout
    .areHorizontallyAligned()                   // Same Y positions
    .areVerticallyAligned()                     // Same X positions
    .areRightAlignedWith()                      // All right edges aligned
    .haveEqualSpacing(expectedGap = 10, horizontal = true)  // Consistent gaps
    .fitWithin(container)                       // All contained within bounds
    .doNotOverlap()                             // No widgets overlap
```

### Complex Layout Example

```kotlin
@Test
fun testComplexApplicationLayout() {
    autoSWT {
        testShell(width = 800, height = 600) {
            // Setup application with toolbar, content area, status bar
        }.test { shell ->
            val composite = runOnSWT { shell.children.first() as Composite }
            val toolbar = shell.find<Composite> { /* find toolbar */ }
            val content = shell.find<Composite> { /* find content */ }
            val statusBar = shell.find<Composite> { /* find status */ }
            
            // Test overall layout structure
            toolbar.assertLayout()
                .isVisible()
                .isNearTopEdgeOf(composite, maxDistance = 0)
                .fillsHorizontally(composite)
            
            content.assertLayout()
                .isVisible()
                .isBelow(toolbar)
                .isAbove(statusBar)  
                .fillsHorizontally(composite)
                .doesNotOverlap(toolbar)
                .doesNotOverlap(statusBar)
            
            statusBar.assertLayout()
                .isVisible()
                .isNearBottomEdgeOf(composite, maxDistance = 0)
                .fillsHorizontally(composite)
                
            // Verify overall layout
            listOf(toolbar, content, statusBar).assertLayout()
                .areAllVisible()
                .areArrangedInColumn(minGap = 0)
                .areLeftAlignedWith()
                .fitWithin(composite)
            
            // Test toolbar button layout
            val toolbarButtons = toolbar.findAll<Button> { true }
            toolbarButtons.assertLayout()
                .areAllVisible()
                .areArrangedInRow(minGap = 5)
                .areTopAlignedWith()
                .fitWithin(toolbar)
        }
    }
}
```

### Error Messages

Layout assertions provide detailed error messages with actual vs expected values:

```
Control should be right-aligned (±0): this=Rectangle{x=100, y=20, width=80, height=25} other=Rectangle{x=200, y=20, width=80, height=25}

Control should be within container: control=Rectangle{x=250, y=30, width=100, height=30} container=Rectangle{x=0, y=0, width=200, height=100} (right edge)

Controls should not overlap: this=Rectangle{x=50, y=20, width=100, height=30} other=Rectangle{x=120, y=25, width=80, height=25}
```

## Display and Shell Management

### Automatic Display Creation

AutoSWT automatically manages SWT Display lifecycle:

```kotlin
// AutoSWT creates TestDisplay automatically
autoSWT {
    // Display is ready for use
    val display = display // Access to current test display
    
    testShell { shell ->
        // Shell is created on the correct display
        // Shell has proper title showing test method name
        // Shell is automatically sized and positioned
    }
}
// Display and all shells are cleaned up automatically
```

### Shell Lifecycle

```kotlin
@Test  
fun testShellLifecycle() {
    autoSWT {
        // 1. AutoSWT detects test method name via stack inspection
        // 2. Creates TestDisplay if not already initialized
        // 3. Sets up single-thread executor for SWT operations
        
        testShell(width = 600, height = 400) { shell ->
            // 4. Shell created with test method name as title
            // 5. Shell sized and positioned
            // 6. FillLayout applied by default
            // 7. Shell made visible
            
            val composite = Composite(shell, SWT.NONE)
            // Your shell setup here
            
        }.test { shell ->
            // 8. Test block executes with shell ready
            // 9. All AutoSWT methods available
            
        }
        // 10. Shell automatically disposed
        // 11. Display reset and cleaned up
    }
}
```

## Resource Cleanup

### Automatic Cleanup Process

AutoSWT ensures complete resource cleanup after each test:

1. **Shell Disposal**: All test shells are disposed automatically
2. **Widget Cleanup**: Child widgets are recursively disposed
3. **Display Reset**: TestDisplay clears all data and state
4. **Memory Management**: Resources are properly released
5. **Thread Cleanup**: Executor threads are managed and cleaned up

### Manual Cleanup (when needed)

```kotlin
// Usually not needed - AutoSWT handles cleanup automatically
autoSWT {
    testShell { shell ->
        val image = Image(shell.display, 100, 100)
        
        // AutoSWT will handle shell disposal, but for custom resources:
        shell.addDisposeListener { 
            image.dispose() // Manual cleanup of custom resources
        }
    }
}
```

## Advanced Features

### Event Simulation

AutoSWT simulates SWT events:

```kotlin
// Text events with proper event chain
textField.typeText("Hello") // Triggers KeyDown, KeyUp, Modify events

// Mouse events with coordinates
button.click(x = 50, y = 20) // Click at specific coordinates
control.doubleClick()        // Double-click simulation
control.rightClick()         // Right-click for context menus

// Keyboard events
control.pressEnter()         // CR key
control.pressTab()           // Tab navigation
control.pressEscape()        // ESC key
```

### State Waiting and Verification

Note: for testing async conditions only.

```kotlin
// Wait for conditions with timeout
val success = waitUntil(timeout = 5000) {
    shell.findAll<Label> { it.text == "Complete" }.isNotEmpty()
}

// Specialized wait methods
waitForText(label, "Expected Text", timeout = 3000)
waitForEnabled(button, timeout = 2000)
waitForVisible(widget, timeout = 1000)

// State checking
assertTrue(control.isDisplayed())
val bounds = control.getAbsoluteBounds()
```

### Visualization Tools

```kotlin
// SVG generation for visual debugging
shell.saveSVG("test-state.svg")
shell.saveScreenshot("test-screenshot.png")

// Take control screenshots
val image = control.takeScreenshot()
image.saveTo(File("widget-screenshot.png"))
```

## SVG Generation

### AutoSWT SVG Integration

```kotlin
@Test
fun testWithSVGGeneration() {
    autoSWT {
        testShell { shell ->
            // Set up your UI
        }.test { shell ->
            // Perform test operations
            
            // Generate SVG at any point in test
            shell.saveSVG("before-interaction.svg")
            
            // Interact with UI
            button.doSelect()
            
            // Generate SVG after changes
            shell.saveSVG("after-interaction.svg")
        }
    }
}
```

### SVG Metadata

Each SVG element includes comprehensive metadata:

```xml
<rect id="widget_1" 
      swt:widget-type="Button" 
      swt:widget-text="Click Me"
      swt:widget-class="org.eclipse.swt.widgets.Button"
      swt:widget-bounds="10,20,100,30"
      swt:widget-enabled="true"
      swt:widget-visible="true"
      swt:x="10" swt:y="20" swt:width="100" swt:height="30" />
```

## Building and Testing

```bash
# Build the project
./gradlew build

# Run tests  
./gradlew test

# Run specific test class
./gradlew test --tests "BasicTest"

# Run with debug output
./gradlew test --info

# Clean and rebuild
./gradlew clean build
```

