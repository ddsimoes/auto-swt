package io.github.ddsimoes.autoswt

import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.Text
import kotlin.test.Test
import kotlin.test.assertEquals

class SimpleListTest {

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

}


