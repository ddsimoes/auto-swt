package io.github.ddsimoes.autoswt

import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Basic test suite
 * Tests core widget creation, layout, and interaction patterns
 */
class BasicTest {

    @Test
    fun testBasicWidgetCreation() {
        autoSWT {
            testShell {
                layout = GridLayout(1, false)
                
                // Create basic widgets
                val label = Label(this, SWT.NONE)
                label.text = "Hello AutoSWT"
                label.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
                
                val button = Button(this, SWT.PUSH)
                button.text = "Click Me"
                button.layoutData = GridData(SWT.CENTER, SWT.CENTER, false, false)
                
                val text = Text(this, SWT.BORDER)
                text.text = "Sample text"
                text.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
            }.test { shell ->
                // Verify widgets exist
                val label = shell.find<Label> { it.text == "Hello AutoSWT" }
                val button = shell.find<Button> { it.text == "Click Me" }
                val text = shell.find<Text> { it.text == "Sample text" }
                
                assertTrue(label.isDisplayed())
                assertTrue(button.isDisplayed()) 
                assertTrue(text.isDisplayed())
                
                // Test basic properties
                assertEquals("Hello AutoSWT", runOnSWT { label.text })
                assertEquals("Click Me", runOnSWT { button.text })
                assertEquals("Sample text", runOnSWT { text.text })
            }
        }
    }

    @Test
    fun testWidgetInteraction() {
        autoSWT {
            testShell {
                layout = GridLayout(2, false)
                
                val label = Label(this, SWT.NONE)
                label.text = "Counter: 0"
                label.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1)
                
                val incrementButton = Button(this, SWT.PUSH)
                incrementButton.text = "Increment"
                incrementButton.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
                
                val decrementButton = Button(this, SWT.PUSH)
                decrementButton.text = "Decrement"  
                decrementButton.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
                
                // Add interaction logic
                var counter = 0
                incrementButton.addListener(SWT.Selection) {
                    counter++
                    label.text = "Counter: $counter"
                }
                
                decrementButton.addListener(SWT.Selection) {
                    counter--
                    label.text = "Counter: $counter"
                }
            }.test { shell ->
                val label = shell.find<Label> { it.text.startsWith("Counter:") }
                val incrementButton = shell.find<Button> { it.text == "Increment" }
                val decrementButton = shell.find<Button> { it.text == "Decrement" }
                
                // Initial state
                assertEquals("Counter: 0", runOnSWT { label.text })
                
                // Test increment
                incrementButton.click()
                assertTrue(waitForText(label, "Counter: 1", 1000))
                
                incrementButton.click()
                assertTrue(waitForText(label, "Counter: 2", 1000))
                
                // Test decrement
                decrementButton.click()
                assertTrue(waitForText(label, "Counter: 1", 1000))
            }
        }
    }

    @Test
    fun testDynamicWidgetCreation() {
        autoSWT {
            testShell {
                layout = GridLayout(1, false)
                
                val statusLabel = Label(this, SWT.NONE)
                statusLabel.text = "Items: 0"
                statusLabel.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
                
                val addButton = Button(this, SWT.PUSH)
                addButton.text = "Add Item"
                addButton.layoutData = GridData(SWT.CENTER, SWT.CENTER, false, false)
                
                val container = Composite(this, SWT.BORDER)
                container.layout = GridLayout(1, false)
                container.layoutData = GridData(SWT.FILL, SWT.FILL, true, true)
                
                // Dynamic item management
                val items = mutableListOf<Label>()
                
                addButton.addListener(SWT.Selection) {
                    val itemLabel = Label(container, SWT.NONE)
                    itemLabel.text = "Item ${items.size + 1}"
                    itemLabel.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
                    items.add(itemLabel)
                    
                    statusLabel.text = "Items: ${items.size}"
                    container.layout()
                    layout()
                }
            }.test { shell ->
                val statusLabel = shell.find<Label> { it.text.startsWith("Items:") }
                val addButton = shell.find<Button> { it.text == "Add Item" }
                
                // Initial state
                assertEquals("Items: 0", runOnSWT { statusLabel.text })
                
                // Add first item
                addButton.click()
                assertTrue(waitForText(statusLabel, "Items: 1", 1000))
                
                // Verify item appears
                assertTrue(waitUntil(1000) {
                    shell.findAll<Label> { it.text == "Item 1" }.isNotEmpty()
                })
                
                // Add more items
                addButton.click()
                addButton.click()
                assertTrue(waitForText(statusLabel, "Items: 3", 1000))
                
                // Verify all items exist
                assertTrue(waitUntil(1000) {
                    val allItems = shell.findAll<Label> { it.text.startsWith("Item ") }
                    allItems.size == 3
                })
            }
        }
    }

    @Test
    fun testFormValidation() {
        autoSWT {
            testShell {
                layout = GridLayout(2, false)
                
                // Name field
                val nameLabel = Label(this, SWT.NONE)
                nameLabel.text = "Name:"
                nameLabel.layoutData = GridData(SWT.RIGHT, SWT.CENTER, false, false)
                
                val nameText = Text(this, SWT.BORDER)
                nameText.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
                
                // Email field
                val emailLabel = Label(this, SWT.NONE) 
                emailLabel.text = "Email:"
                emailLabel.layoutData = GridData(SWT.RIGHT, SWT.CENTER, false, false)
                
                val emailText = Text(this, SWT.BORDER)
                emailText.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
                
                // Validation status
                val validationLabel = Label(this, SWT.NONE)
                validationLabel.text = "Form is invalid"
                validationLabel.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1)
                
                // Submit button
                val submitButton = Button(this, SWT.PUSH)
                submitButton.text = "Submit"
                submitButton.enabled = false
                submitButton.layoutData = GridData(SWT.CENTER, SWT.CENTER, false, false, 2, 1)
                
                // Result label
                val resultLabel = Label(this, SWT.NONE)
                resultLabel.text = ""
                resultLabel.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1)
                
                // Validation logic
                val validateForm = {
                    val nameValid = nameText.text.trim().isNotEmpty()
                    val emailValid = emailText.text.contains("@")
                    val isValid = nameValid && emailValid
                    
                    validationLabel.text = if (isValid) "Form is valid" else "Form is invalid"
                    submitButton.enabled = isValid
                }
                
                nameText.addListener(SWT.Modify) { validateForm() }
                emailText.addListener(SWT.Modify) { validateForm() }
                
                submitButton.addListener(SWT.Selection) {
                    resultLabel.text = "Submitted: ${nameText.text} (${emailText.text})"
                }
            }.test { shell ->
                // Wait for UI to stabilize
                
                val allTexts = shell.findAll<Text>()
                val nameText = allTexts[0] // First text field
                val emailText = allTexts[1] // Second text field
                val validationLabel = shell.find<Label> { it.text.contains("Form is") }
                val submitButton = shell.find<Button> { it.text == "Submit" }
                val resultLabel = shell.find<Label> { it.text == "" }
                
                // Initial state - form should be invalid
                assertEquals("Form is invalid", runOnSWT { validationLabel.text })
                assertEquals(false, runOnSWT { submitButton.enabled })
                
                // Fill name only - should still be invalid
                nameText.typeText("John Doe")
                assertTrue(waitForText(validationLabel, "Form is invalid", 1000))
                assertEquals(false, runOnSWT { submitButton.enabled })
                
                // Fill email - should become valid
                emailText.typeText("john@example.com")
                assertTrue(waitForText(validationLabel, "Form is valid", 1000))
                
                // Wait for button to be enabled using a more robust method
                assertTrue(waitUntil(2000) {
                    runOnSWT { submitButton.enabled }
                })
                
                // Submit form
                submitButton.click()
                assertTrue(waitForText(resultLabel, "Submitted: John Doe (john@example.com)", 1000))
                
                // Clear name - should become invalid again
                nameText.clearText()
                assertTrue(waitForText(validationLabel, "Form is invalid", 1000))
                assertEquals(false, runOnSWT { submitButton.enabled })
            }
        }
    }

    @Test
    fun testListManagement() {
        autoSWT {
            testShell {
                layout = GridLayout(1, false)
                
                // Header
                val headerLabel = Label(this, SWT.NONE)
                headerLabel.text = "Task Manager"
                headerLabel.layoutData = GridData(SWT.CENTER, SWT.CENTER, true, false)
                
                // Input section
                val inputComposite = Composite(this, SWT.NONE)
                inputComposite.layout = GridLayout(2, false)
                inputComposite.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
                
                val inputText = Text(inputComposite, SWT.BORDER)
                inputText.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
                
                val addButton = Button(inputComposite, SWT.PUSH)
                addButton.text = "Add Task"
                addButton.layoutData = GridData(SWT.CENTER, SWT.CENTER, false, false)
                
                // Status
                val statusLabel = Label(this, SWT.NONE)
                statusLabel.text = "Tasks: 0"
                statusLabel.layoutData = GridData(SWT.CENTER, SWT.CENTER, true, false)
                
                // Task list container
                val listContainer = Composite(this, SWT.BORDER or SWT.V_SCROLL)
                listContainer.layout = GridLayout(1, false)
                listContainer.layoutData = GridData(SWT.FILL, SWT.FILL, true, true)
                
                // Task management
                val tasks = mutableListOf<Composite>()
                
                val updateStatus = {
                    statusLabel.text = "Tasks: ${tasks.size}"
                }
                
                val addTask = { taskText: String ->
                    val taskComposite = Composite(listContainer, SWT.NONE)
                    taskComposite.layout = GridLayout(3, false)
                    taskComposite.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
                    
                    val taskLabel = Label(taskComposite, SWT.NONE)
                    taskLabel.text = taskText
                    taskLabel.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
                    
                    val completeButton = Button(taskComposite, SWT.PUSH)
                    completeButton.text = "✓"
                    completeButton.layoutData = GridData(SWT.CENTER, SWT.CENTER, false, false)
                    
                    val deleteButton = Button(taskComposite, SWT.PUSH)
                    deleteButton.text = "✗"
                    deleteButton.layoutData = GridData(SWT.CENTER, SWT.CENTER, false, false)
                    
                    completeButton.addListener(SWT.Selection) {
                        val currentText = taskLabel.text
                        taskLabel.text = if (currentText.startsWith("✓ ")) {
                            currentText.substring(2)
                        } else {
                            "✓ $currentText"
                        }
                    }
                    
                    deleteButton.addListener(SWT.Selection) {
                        tasks.remove(taskComposite)
                        taskComposite.dispose()
                        updateStatus()
                        listContainer.layout()
                        layout()
                    }
                    
                    tasks.add(taskComposite)
                    updateStatus()
                    listContainer.layout()
                    layout()
                }
                
                addButton.addListener(SWT.Selection) {
                    val text = inputText.text.trim()
                    if (text.isNotEmpty()) {
                        addTask(text)
                        inputText.text = ""
                    }
                }
                
                inputText.addListener(SWT.KeyDown) { event ->
                    if (event.keyCode == 13) { // CR
                        val text = inputText.text.trim()
                        if (text.isNotEmpty()) {
                            addTask(text)
                            inputText.text = ""
                        }
                    }
                }
            }.test { shell ->
                val headerLabel = shell.find<Label> { it.text == "Task Manager" }
                val inputText = shell.find<Text>()
                val addButton = shell.find<Button> { it.text == "Add Task" }
                val statusLabel = shell.find<Label> { it.text.startsWith("Tasks:") }
                
                // Verify initial state
                assertTrue(headerLabel.isDisplayed())
                assertEquals("Tasks: 0", runOnSWT { statusLabel.text })
                
                // Add first task
                inputText.typeText("Buy groceries")
                addButton.click()
                
                assertTrue(waitForText(statusLabel, "Tasks: 1", 1000))
                assertEquals("", runOnSWT { inputText.text })
                
                // Verify task appears
                assertTrue(waitUntil(1000) {
                    shell.findAll<Label> { it.text == "Buy groceries" }.isNotEmpty()
                })
                
                // Add second task using Enter key
                inputText.typeText("Walk the dog")
                inputText.pressEnter()
                
                assertTrue(waitForText(statusLabel, "Tasks: 2", 1000))
                
                // Test complete functionality
                val completeButton = shell.find<Button> { it.text == "✓" }
                completeButton.click()

                // Verify task is marked complete
                assertTrue(waitUntil(1000) {
                    shell.findAll<Label> { it.text.startsWith("✓ ") }.isNotEmpty()
                })
                
                // Test delete functionality
                val deleteButton = shell.find<Button> { it.text == "✗" }
                deleteButton.click()
                
                assertTrue(waitForText(statusLabel, "Tasks: 1", 1000))
            }
        }
    }

    @Test
    fun testTabularData() {
        autoSWT {
            testShell(600, 400) {
                layout = GridLayout(1, false)
                
                val titleLabel = Label(this, SWT.NONE)
                titleLabel.text = "Employee Database"
                titleLabel.layoutData = GridData(SWT.CENTER, SWT.CENTER, true, false)
                
                // Use a simpler list instead of Table for testing
                val employeeLabel = Label(this, SWT.NONE)
                employeeLabel.text = "Employees: 3"
                employeeLabel.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
                
                val listComposite = Composite(this, SWT.BORDER)
                listComposite.layout = GridLayout(1, false)
                listComposite.layoutData = GridData(SWT.FILL, SWT.FILL, true, true)
                
                // Add initial employees as labels
                val employees = mutableListOf(
                    "John Doe - john@company.com - Engineering",
                    "Jane Smith - jane@company.com - Marketing", 
                    "Bob Johnson - bob@company.com - Sales"
                )
                
                val employeeLabels = mutableListOf<Label>()
                
                val refreshList = {
                    employeeLabels.forEach { it.dispose() }
                    employeeLabels.clear()
                    
                    employees.forEach { employee ->
                        val empLabel = Label(listComposite, SWT.NONE)
                        empLabel.text = employee
                        empLabel.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
                        employeeLabels.add(empLabel)
                    }
                    
                    employeeLabel.text = "Employees: ${employees.size}"
                    listComposite.layout()
                    layout()
                }
                
                refreshList()
                
                // Action buttons
                val buttonComposite = Composite(this, SWT.NONE)
                buttonComposite.layout = GridLayout(2, false)
                buttonComposite.layoutData = GridData(SWT.CENTER, SWT.CENTER, false, false)
                
                val addButton = Button(buttonComposite, SWT.PUSH)
                addButton.text = "Add Employee"
                
                val removeButton = Button(buttonComposite, SWT.PUSH)
                removeButton.text = "Remove Last"
                removeButton.enabled = employees.isNotEmpty()
                
                addButton.addListener(SWT.Selection) {
                    employees.add("New Employee - new@company.com - HR")
                    refreshList()
                    removeButton.enabled = employees.isNotEmpty()
                }
                
                removeButton.addListener(SWT.Selection) {
                    if (employees.isNotEmpty()) {
                        employees.removeLast()
                        refreshList()
                        removeButton.enabled = employees.isNotEmpty()
                    }
                }
            }.test { shell ->

                val titleLabel = shell.find<Label> { it.text == "Employee Database" }
                val employeeLabel = shell.find<Label> { it.text.startsWith("Employees:") }
                val addButton = shell.find<Button> { it.text == "Add Employee" }
                val removeButton = shell.find<Button> { it.text == "Remove Last" }
                
                // Verify initial state
                assertTrue(titleLabel.isDisplayed())
                assertEquals("Employees: 3", runOnSWT { employeeLabel.text })
                assertTrue(runOnSWT { removeButton.enabled })
                
                // Verify initial employees exist
                assertTrue(waitUntil(1000) {
                    shell.findAll<Label> { it.text.contains("John Doe") }.isNotEmpty()
                })
                
                // Test add employee
                addButton.click()
                assertTrue(waitForText(employeeLabel, "Employees: 4", 1000))
                
                // Verify new employee appears
                assertTrue(waitUntil(1000) {
                    shell.findAll<Label> { it.text.contains("New Employee") }.isNotEmpty()
                })
                
                // Test remove employee
                removeButton.click()
                assertTrue(waitForText(employeeLabel, "Employees: 3", 1000))
                
                // Remove all employees
                repeat(3) {
                    removeButton.click()
                }
                
                assertTrue(waitForText(employeeLabel, "Employees: 0", 1000))
                assertEquals(false, runOnSWT { removeButton.enabled })
            }
        }
    }
}