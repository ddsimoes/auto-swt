package io.github.ddsimoes.autoswt

import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class SWTSvgGeneratorTest {

    @BeforeEach
    fun setup() {
    }

    @Test
    fun testBasicSvgGeneration() {
        autoSWT {
            testShell(400, 300) {
                layout = GridLayout(1, false)
                
                val label = Label(this, SWT.NONE)
                label.text = "Hello SVG"
                label.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
                
                val button = Button(this, SWT.PUSH)
                button.text = "Click me"
                button.layoutData = GridData(SWT.CENTER, SWT.CENTER, false, false)
            }.test { shell ->
                val generator = SWTSvgGenerator()
                val svg = runOnSWT { generator.generateSvg(shell) }

                // Basic structure checks
                assertTrue(svg.contains("<?xml version=\"1.0\""))
                assertTrue(svg.contains("<svg"))
                assertTrue(svg.contains("xmlns=\"http://www.w3.org/2000/svg\""))
                assertTrue(svg.contains("xmlns:swt=\"https://ddsimoes.github.io/auto-swt\""))
                assertTrue(svg.contains("</svg>"))
                
                // Check for our widgets
                assertTrue(svg.contains("Hello SVG"))
                assertTrue(svg.contains("Click me"))
                
                // Check for metadata
                assertTrue(svg.contains("swt:widget-type"))
                assertTrue(svg.contains("swt:x"))
                assertTrue(svg.contains("swt:y"))
            }
        }
    }

    @Test
    fun testMetadataGeneration() {
        autoSWT {
            testShell {
                layout = null // Allow absolute positioning
                val label = Label(this, SWT.NONE)
                label.text = "Test Label"
                label.setBounds(10, 20, 100, 30)
            }.test { shell ->
                val generator = SWTSvgGenerator()
                val svg = runOnSWT { generator.generateSvg(shell) }

                // Check specific metadata
                assertTrue(svg.contains("swt:widget-type=\"Label\""))
                assertTrue(svg.contains("swt:widget-text=\"Test Label\""))
                assertTrue(svg.contains("swt:x=\"10\""))
                assertTrue(svg.contains("swt:y=\"20\""))
                assertTrue(svg.contains("swt:width=\"100\""))
                assertTrue(svg.contains("swt:height=\"30\""))
            }
        }
    }

    @Test
    fun testNestedComposites() {
        autoSWT {
            testShell {
                layout = GridLayout(1, false)
                
                val outerComposite = Composite(this, SWT.BORDER)
                outerComposite.setBounds(10, 10, 200, 150)
                outerComposite.layout = GridLayout(1, false)
                
                val innerComposite = Composite(outerComposite, SWT.BORDER)
                innerComposite.setBounds(5, 5, 180, 100)
                
                val label = Label(innerComposite, SWT.NONE)
                label.text = "Nested Label"
                label.setBounds(5, 5, 100, 20)
            }.test { shell ->
                val generator = SWTSvgGenerator()
                val svg = runOnSWT { generator.generateSvg(shell) }

                // Check for nested groups
                assertTrue(svg.contains("<g"))
                assertTrue(svg.contains("transform=\"translate("))
                
                // Check for nested structure
                assertTrue(svg.contains("swt:widget-type=\"Composite\""))
                assertTrue(svg.contains("Nested Label"))
            }
        }
    }

    @Test
    fun testCheckboxAndRadioRendering() {
        autoSWT {
            testShell {
                layout = GridLayout(3, false)
                
                val checkbox = Button(this, SWT.CHECK)
                checkbox.text = "Checkbox"
                checkbox.selection = true
                checkbox.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
                
                val radio = Button(this, SWT.RADIO)
                radio.text = "Radio"
                radio.selection = false
                radio.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
                
                val normalButton = Button(this, SWT.PUSH)
                normalButton.text = "Normal"
                normalButton.layoutData = GridData(SWT.FILL, SWT.CENTER, true, false)
            }.test { shell ->
                val generator = SWTSvgGenerator()
                val svg = runOnSWT { generator.generateSvg(shell) }

                // Check for different button types
                assertTrue(svg.contains("Checkbox"))
                assertTrue(svg.contains("Radio"))
                assertTrue(svg.contains("Normal"))
                
                // Should contain metadata for different button types
                assertTrue(svg.contains("swt:widget-type"))
            }
        }
    }

    @Test
    fun testSvgFileGeneration() {
        autoSWT {
            testShell {
                val label = Label(this, SWT.NONE)
                label.text = "File Test"
                label.setBounds(10, 10, 100, 30)
            }.test { shell ->
                val filename = "test_output.svg"
                val generator = SWTSvgGenerator()
                
                // Clean up any existing file
                val file = File(filename)
                if (file.exists()) {
                    file.delete()
                }
                
                runOnSWT {
                    generator.generateToFile(shell, filename)
                }

                assertTrue(file.exists())
                assertTrue(file.length() > 0)
                
                val content = file.readText()
                assertTrue(content.contains("File Test"))
                assertTrue(content.contains("<?xml"))
                assertTrue(content.contains("</svg>"))
                
                // Clean up
                file.delete()
            }
        }
    }

    @Test
    fun testCompanionMethods() {
        autoSWT {
            testShell {
                val label = Label(this, SWT.NONE)
                label.text = "Companion Test"
                label.setBounds(10, 10, 100, 30)
            }.test { shell ->
                // Test static utility methods
                val svg1 = runOnSWT { SWTSvgGenerator.generateFromShell(shell) }
                assertTrue(svg1.contains("Companion Test"))
                
                val filename = "companion_test.svg"
                val file = File(filename)
                if (file.exists()) {
                    file.delete()
                }
                
                runOnSWT {
                    SWTSvgGenerator.saveFromShell(shell, filename)
                }
                assertTrue(file.exists())
                
                val content = file.readText()
                assertTrue(content.contains("Companion Test"))
                
                // Clean up
                file.delete()
            }
        }
    }
}