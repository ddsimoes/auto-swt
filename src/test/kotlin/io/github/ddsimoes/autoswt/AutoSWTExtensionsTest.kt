package io.github.ddsimoes.autoswt

import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Rectangle
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.Text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoSWTExtensionsTest {

    @Test
    fun testKeyboardInteractions() {
        autoSWT {
            testShell {
                val composite = Composite(this, SWT.NONE)
                composite.layout = GridLayout(2, false)
                
                val textField = Text(composite, SWT.BORDER)
                textField.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
                
                val resultLabel = Label(composite, SWT.NONE)
                resultLabel.text = "Empty"
                resultLabel.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
                
                // Add listener to update label when text changes
                textField.addListener(SWT.Modify) {
                    resultLabel.text = textField.text.ifEmpty { "Empty" }
                }
            }.test { shell ->
                val textField = shell.find<Text>()
                val resultLabel = shell.find<Label> { it.text == "Empty" }
                
                // Test typeText
                textField.typeText("Hello World")
                assertEquals("Hello World", runOnSWT { textField.text })
                assertEquals("Hello World", runOnSWT { resultLabel.text })
                
                // Test clearText
                textField.clearText()
                assertEquals("", runOnSWT { textField.text })
                assertEquals("Empty", runOnSWT { resultLabel.text })
                
                // Test pressEnter (should work even with empty text)
                textField.typeText("Test Enter")
                textField.pressEnter()
                assertEquals("Test Enter", runOnSWT { textField.text })
            }
        }
    }

    @Test
    fun testMouseInteractions() {
        autoSWT {
            testShell {
                val composite = Composite(this, SWT.NONE)
                composite.layout = GridLayout(3, false)
                
                val clickButton = Button(composite, SWT.PUSH)
                clickButton.text = "Click Me"
                
                val doubleClickButton = Button(composite, SWT.PUSH)
                doubleClickButton.text = "Double Click"
                
                val rightClickButton = Button(composite, SWT.PUSH)
                rightClickButton.text = "Right Click"
                
                val statusLabel = Label(composite, SWT.NONE)
                statusLabel.text = "No action"
                statusLabel.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1)
                
                // Add listeners
                clickButton.addListener(SWT.Selection) {
                    statusLabel.text = "Clicked"
                }
                
                doubleClickButton.addListener(SWT.MouseDoubleClick) {
                    statusLabel.text = "Double Clicked"
                }
                
                rightClickButton.addListener(SWT.MouseUp) { event ->
                    if (event.button == 3) {
                        statusLabel.text = "Right Clicked"
                    }
                }
            }.test { shell ->
                val clickButton = shell.find<Button> { it.text == "Click Me" }
                val doubleClickButton = shell.find<Button> { it.text == "Double Click" }
                val rightClickButton = shell.find<Button> { it.text == "Right Click" }
                val statusLabel = shell.find<Label> { it.text == "No action" }
                
                // Test click
                clickButton.click()
                assertEquals("Clicked", runOnSWT { statusLabel.text })
                
                // Test double click
                doubleClickButton.doubleClick()
                assertEquals("Double Clicked", runOnSWT { statusLabel.text })
                
                // Test right click
                rightClickButton.rightClick()
                assertEquals("Right Clicked", runOnSWT { statusLabel.text })
            }
        }
    }

    @Test
    fun testWaitersAndCheckers() {
        autoSWT {
            testShell {
                val composite = Composite(this, SWT.NONE)
                composite.layout = GridLayout(2, false)
                
                val delayButton = Button(composite, SWT.PUSH)
                delayButton.text = "Start Delay"
                
                val statusLabel = Label(composite, SWT.NONE)
                statusLabel.text = "Ready"
                
                val disabledButton = Button(composite, SWT.PUSH)
                disabledButton.text = "Disabled"
                disabledButton.enabled = false
                
                // Add listener to simulate delayed action
                delayButton.addListener(SWT.Selection) {
                    statusLabel.text = "Processing..."
                    
                    // Simulate async work with a timer
                    val display = shell.display
                    display.timerExec(500) {
                        statusLabel.text = "Done"
                        disabledButton.enabled = true
                    }
                }
            }.test { shell ->
                val delayButton = shell.find<Button> { it.text == "Start Delay" }
                val statusLabel = shell.find<Label> { it.text == "Ready" }
                val disabledButton = shell.find<Button> { it.text == "Disabled" }
                
                // Test initial state
                assertTrue(statusLabel.isDisplayed())
                assertFalse(runOnSWT { disabledButton.isEnabled })
                
                // Test waitForText with immediate condition
                assertTrue(waitForText(statusLabel, "Ready", 1000))
                
                // Start delayed action
                delayButton.click()
                
                // Test waitUntil for processing state
                assertTrue(waitUntil(1000) { 
                    runOnSWT { statusLabel.text == "Processing..." }
                })
                
                // Test waitForText for completion
                assertTrue(waitForText(statusLabel, "Done", 2000))
                
                // Test waitForEnabled
                assertTrue(waitForEnabled(disabledButton, 1000))
                
                // Test bounds checking
                val bounds = runOnSWT { statusLabel.bounds }
                assertTrue(bounds.width > 0)
                assertTrue(bounds.height > 0)
                
                val absoluteBounds = runOnSWT { 
                    val bounds = statusLabel.bounds
                    val location = statusLabel.toDisplay(0, 0)
                    Rectangle(location.x, location.y, bounds.width, bounds.height)
                }
                assertTrue(absoluteBounds.x >= 0)
                assertTrue(absoluteBounds.y >= 0)
            }
        }
    }

    @Test
    fun testAdvancedInteractions() {
        autoSWT {
            testShell {
                val composite = Composite(this, SWT.NONE)
                composite.layout = GridLayout(1, false)
                
                val textField = Text(composite, SWT.BORDER)
                textField.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
                
                val button = Button(composite, SWT.PUSH)
                button.text = "Process"
                button.enabled = false
                
                val resultLabel = Label(composite, SWT.NONE)
                resultLabel.text = "Enter text to enable button"
                
                // Enable button only when text is not empty
                textField.addListener(SWT.Modify) {
                    val hasText = textField.text.trim().isNotEmpty()
                    button.enabled = hasText
                    resultLabel.text = if (hasText) "Button enabled" else "Enter text to enable button"
                }
                
                button.addListener(SWT.Selection) {
                    resultLabel.text = "Processed: ${textField.text}"
                }
            }.test { shell ->
                val textField = shell.find<Text>()
                val button = shell.find<Button> { it.text == "Process" }
                val resultLabel = shell.find<Label>()
                
                // Initially button should be disabled
                assertFalse(waitForEnabled(button, 100))
                
                // Type text and wait for button to be enabled
                textField.typeText("Test input")
                assertTrue(waitForEnabled(button, 1000))
                assertTrue(waitForText(resultLabel, "Button enabled", 1000))
                
                // Click button and verify result
                button.click()
                assertTrue(waitForText(resultLabel, "Processed: Test input", 1000))
                
                // Clear text and verify button is disabled again
                textField.clearText()
                assertFalse(waitForEnabled(button, 1000))
                assertTrue(waitForText(resultLabel, "Enter text to enable button", 1000))
            }
        }
    }

    @Test
    fun testComplexWorkflow() {
        autoSWT {
            testShell(500, 400) {
                // Create a simple form with validation
                SimpleList(this, SWT.NONE)
            }.test { shell ->
                val textField = shell.find<Text>()
                val addButton = shell.find<Button> { it.text == "Add" }
                val countLabel = shell.find<Label> { it.text.startsWith("Number of items:") }
                
                // Test adding multiple items
                val items = listOf("First item", "Second item", "Third item")
                
                items.forEachIndexed { index, item ->
                    textField.typeText(item)
                    addButton.click()
                    
                    // Wait for counter to update
                    assertTrue(waitForText(countLabel, "Number of items: ${index + 1}", 1000))
                    
                    // Verify text field is cleared
                    assertEquals("", runOnSWT { textField.text })
                }
                
                // Test removing items (find remove buttons)
                val removeButtons = shell.findAll<Button> { it.text == "X" }
                assertEquals(3, removeButtons.size)
                
                // Remove first item
                removeButtons.first().click()
                assertTrue(waitForText(countLabel, "Number of items: 2", 1000))
                
                // Verify we now have 2 remove buttons
                val remainingButtons = shell.findAll<Button> { it.text == "X" }
                assertEquals(2, remainingButtons.size)
            }
        }
    }
}