package io.github.ddsimoes.autoswt

import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Label
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Debug test to understand SVG coordinate issues
 */
class SvgDebugTest {
    
    @BeforeEach
    fun setup() {
    }
    
    @Test
    fun debugSimpleUI() {
        autoSWT {
            testShell(300, 200) {
                layout = GridLayout(1, false)
                text = "Debug Test"
                
                // Create simple UI
                val label = Label(this, SWT.NONE)
                label.text = "Test Label"
                
                val button = Button(this, SWT.PUSH)
                button.text = "Test Button"
                
                pack()
            }.test { shell ->
                // Debug shell and widget bounds
                runOnSWT {
                    println("Shell bounds: ${shell.bounds}")
                    println("Shell size: ${shell.size}")
                    println("Shell client area: ${shell.clientArea}")
                    
                    for (child in shell.children) {
                        println("Child ${child.javaClass.simpleName}: bounds=${child.bounds}")
                        if (child is Label) println("  Label text: '${child.text}'")
                        if (child is Button) println("  Button text: '${child.text}'")
                    }
                }
                
                // Generate SVG
                val generator = SWTSvgGenerator()
                val svg = runOnSWT { generator.generateSvg(shell) }
                
                println("\nGenerated SVG (first 500 chars):")
                println(svg.take(500))
                
                // Look for coordinate issues
                val lines = svg.lines()
                lines.filter { it.contains("<rect") || it.contains("<text") }
                    .take(10)
                    .forEach { println("SVG element: ${it.trim()}") }
            }
        }
    }
}