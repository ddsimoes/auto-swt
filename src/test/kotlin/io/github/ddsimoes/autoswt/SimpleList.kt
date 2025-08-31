package io.github.ddsimoes.autoswt

import org.eclipse.swt.SWT
import org.eclipse.swt.custom.ScrolledComposite
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.*

class SimpleList(parent: Composite, style: Int) : Composite(parent, style) {

    private val items = mutableListOf<String>()
    private lateinit var textField: Text
    private lateinit var scrolledComposite: ScrolledComposite
    private lateinit var itemsContainer: Composite
    private lateinit var countLabel: Label

    init {
        initControls()
    }

    private fun initControls() {
        layout = GridLayout(1, false)
        
        // Top row: text field + add button
        val topComposite = Composite(this, SWT.NONE)
        topComposite.layout = GridLayout(2, false)
        topComposite.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
        
        textField = Text(topComposite, SWT.BORDER)
        textField.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
        
        val addButton = Button(topComposite, SWT.PUSH)
        addButton.text = "Add"
        addButton.layoutData = GridData(SWT.CENTER, SWT.CENTER, false, false)
        
        // Middle: scrollable list of items (grows with available space)
        scrolledComposite = ScrolledComposite(this, SWT.V_SCROLL or SWT.BORDER)
        scrolledComposite.layoutData = GridData(SWT.FILL, SWT.FILL, true, true)
        
        itemsContainer = Composite(scrolledComposite, SWT.NONE)
        itemsContainer.layout = GridLayout(1, false)
        scrolledComposite.content = itemsContainer
        scrolledComposite.setExpandHorizontal(true)
        scrolledComposite.setExpandVertical(true)
        
        // Bottom: counter label
        countLabel = Label(this, SWT.NONE)
        countLabel.text = "Number of items: 0"
        countLabel.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
        
        // Event handlers
        addButton.addSelectionListener(object : SelectionAdapter() {
            override fun widgetSelected(e: SelectionEvent) {
                addItem()
            }
        })
        
        textField.addListener(SWT.KeyDown) { event ->
            if (event.keyCode == SWT.CR.code || event.keyCode == SWT.KEYPAD_CR) {
                addItem()
            }
        }
    }
    
    private fun addItem() {
        val text = textField.text.trim()
        if (text.isNotEmpty()) {
            items.add(text)
            textField.text = ""
            refreshItemsList()
            updateCounter()
        }
    }
    
    private fun removeItem(index: Int) {
        if (index >= 0 && index < items.size) {
            items.removeAt(index)
            refreshItemsList()
            updateCounter()
        }
    }
    
    private fun refreshItemsList() {
        // Clear existing items
        itemsContainer.children.forEach { it.dispose() }
        
        // Add each item with remove button
        items.forEachIndexed { index, item ->
            val itemComposite = Composite(itemsContainer, SWT.NONE)
            itemComposite.layout = GridLayout(2, false)
            itemComposite.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
            
            val itemLabel = Label(itemComposite, SWT.NONE)
            itemLabel.text = item
            itemLabel.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
            
            val removeButton = Button(itemComposite, SWT.PUSH)
            removeButton.text = "X"
            removeButton.layoutData = GridData(SWT.CENTER, SWT.CENTER, false, false)
            
            removeButton.addSelectionListener(object : SelectionAdapter() {
                override fun widgetSelected(e: SelectionEvent) {
                    removeItem(index)
                }
            })
        }
        
        // Update scroll area
        itemsContainer.layout()
        val size = itemsContainer.computeSize(SWT.DEFAULT, SWT.DEFAULT)
        scrolledComposite.setMinSize(size)
        scrolledComposite.layout(true)
        layout(true)
    }
    
    private fun updateCounter() {
        countLabel.text = "Number of items: ${items.size}"
    }
}


fun main() {
    val display = Display()
    val shell = Shell(display)
    shell.text = "Simple List Demo"
    shell.setSize(400, 300)
    shell.layout = GridLayout(1, false)
    
    val simpleList = SimpleList(shell, SWT.NONE)
    simpleList.layoutData = GridData(SWT.FILL, SWT.FILL, true, true)
    
    shell.open()

    while (!shell.isDisposed) {
        if (!display.readAndDispatch()) {
            display.sleep()
        }
    }

    display.dispose()
}