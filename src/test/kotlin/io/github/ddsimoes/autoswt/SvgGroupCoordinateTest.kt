package io.github.ddsimoes.autoswt

import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.Text
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Test SVG generation with nested composites to verify group-based coordinate system
 */
class SvgGroupCoordinateTest {
    
    @BeforeEach
    fun setup() {
    }
    
    @Test
    fun testNestedCompositeCoordinates() {
        autoSWT {
            testShell(400, 300) {
                text = "Nested Layout Test"
                layout = null // Allow absolute positioning
                // Create nested composite structure
                val mainComposite = Composite(this, SWT.BORDER)
                mainComposite.setBounds(10, 10, 350, 250)
                mainComposite.layout = null // Allow absolute positioning for children
                
                val leftPanel = Composite(mainComposite, SWT.BORDER)
                leftPanel.setBounds(5, 5, 160, 240)
                
                val rightPanel = Composite(mainComposite, SWT.BORDER)  
                rightPanel.setBounds(170, 5, 160, 240)
                
                // Add widgets to left panel (should use relative coordinates within leftPanel)
                val leftLabel = Label(leftPanel, SWT.NONE)
                leftLabel.text = "Left Panel"
                leftLabel.setBounds(10, 10, 80, 20)
                
                val leftButton = Button(leftPanel, SWT.PUSH)
                leftButton.text = "Left Button"
                leftButton.setBounds(10, 40, 100, 30)
                
                // Add widgets to right panel (should use relative coordinates within rightPanel)
                val rightLabel = Label(rightPanel, SWT.NONE)
                rightLabel.text = "Right Panel"
                rightLabel.setBounds(10, 10, 80, 20)
                
                val rightText = Text(rightPanel, SWT.BORDER)
                rightText.text = "Right Text"
                rightText.setBounds(10, 40, 120, 25)
            }.test { shell ->
                // Debug actual SWT coordinates
                runOnSWT {
                    println("=== SWT Coordinate Debug ===")
                    println("Shell: ${shell.bounds}")
                    
                    val mainComposite = shell.children.first() as Composite
                    println("MainComposite: ${mainComposite.bounds}")
                    
                    val leftPanel = mainComposite.children[0] as Composite
                    val rightPanel = mainComposite.children[1] as Composite
                    println("LeftPanel: ${leftPanel.bounds}")
                    println("RightPanel: ${rightPanel.bounds}")
                    
                    val leftLabel = leftPanel.children[0] as Label
                    val leftButton = leftPanel.children[1] as Button
                    println("LeftLabel: ${leftLabel.bounds}")
                    println("LeftButton: ${leftButton.bounds}")
                    
                    val rightLabel = rightPanel.children[0] as Label
                    val rightText = rightPanel.children[1] as Text
                    println("RightLabel: ${rightLabel.bounds}")
                    println("RightText: ${rightText.bounds}")
                }
                
                // Generate SVG with group-based coordinates
                val generator = SWTSvgGenerator()
                val svg = runOnSWT { generator.generateSvg(shell) }
                
                println("\n=== Generated SVG Analysis ===")
                println("SVG length: ${svg.length} characters")
                
                // Analyze SVG structure
                val lines = svg.lines()
                
                // Find group elements
                println("\nSVG Groups:")
                lines.filter { it.contains("<g ") }
                    .forEach { println("  ${it.trim()}") }
                
                // Find elements with SWT coordinate metadata  
                println("\nSWT Coordinate Metadata:")
                lines.filter { it.contains("swt:") }
                    .take(8)
                    .forEach { 
                        val coords = extractCoordinates(it)
                        println("  $coords")
                    }
                
                // Verify that nested elements use group transforms
                assertTrue(svg.contains("transform=\"translate("), "Should have transform elements")
                assertTrue(svg.contains("swt:widget-type=\"Composite\""), "Should have composite metadata")
                
                // Print actual transforms for debugging
                println("\\nActual transforms found:")
                svg.lines().filter { it.contains("transform=") }.forEach { 
                    println("  ${it.trim()}")
                }
            }
        }
    }
    
    private fun extractCoordinates(line: String): String {
        val swtX = extractAttribute(line, "swt:x")
        val swtY = extractAttribute(line, "swt:y")
        val swtW = extractAttribute(line, "swt:width")
        val swtH = extractAttribute(line, "swt:height")
        val type = extractAttribute(line, "swt:widget-type")
        val text = extractAttribute(line, "swt:widget-text")
        
        return "$type '$text': SWT($swtX,$swtY) ${swtW}x$swtH"
    }
    
    private fun extractAttribute(line: String, attrName: String): String {
        val pattern = """$attrName="([^"]*)"*""".toRegex()
        return pattern.find(line)?.groupValues?.get(1) ?: "?"
    }
    
    private fun assertTrue(condition: Boolean, message: String) {
        if (!condition) {
            throw AssertionError(message)
        }
    }
}