package io.github.ddsimoes.autoswt

import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.graphics.Rectangle
import org.eclipse.swt.widgets.*
import java.io.File

/**
 * Generates SVG representations of SWT widget hierarchies with visual approximations
 * and metadata for testing and debugging purposes.
 */
class SWTSvgGenerator {
    
    private val svgBuilder = StringBuilder()
    private var currentId = 0
    
    /**
     * Generate SVG from a shell and save to file
     */
    fun generateToFile(composite: Composite, filename: String) {
        val svg = generateSvg(composite)
        File(filename).writeText(svg)
    }
    
    /**
     * Generate SVG string from a shell
     */
    fun generateSvg(composite: Composite): String {
        reset()
        
        val bounds = composite.bounds
        val svgWidth = bounds.width
        val svgHeight = bounds.height
        
        // SVG header
        svgBuilder.append("""
            <?xml version="1.0" encoding="UTF-8"?>
            <svg xmlns="http://www.w3.org/2000/svg" 
                 xmlns:swt="https://ddsimoes.github.io/auto-swt"
                 width="$svgWidth" 
                 height="$svgHeight" 
                 viewBox="0 0 $svgWidth $svgHeight">
            <defs>
                <style type="text/css"><![CDATA[
                    .widget-text { font-family: Arial, sans-serif; font-size: 11px; }
                    .widget-border { stroke: #cccccc; stroke-width: 1; fill: none; }
                    .button-border { stroke: #666666; stroke-width: 1; fill: #f0f0f0; }
                    .text-border { stroke: #999999; stroke-width: 1; fill: white; }
                    .label-text { fill: #000000; }
                    .metadata { font-size: 8px; fill: #666666; }
                ]]></style>
            </defs>
            
        """.trimIndent())

        val text = if (composite is Shell) composite.text else "Composite"

        // Add shell background
        svgBuilder.append(createRectElement(
            x = 0, y = 0, 
            width = svgWidth, height = svgHeight,
            fill = colorToHex(composite.background ?: composite.display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND)),
            className = "shell-background",
            metadata = createMetadata("Shell", text, composite)
        ))
        
        // Create root group for shell content
        svgBuilder.append("""
            <g id="shell_content" ${createMetadata("ShellContent", "", composite)}>
        """.trimIndent())
        
        // Process all child widgets using the new group-based approach
        for (child in composite.children) {
            processWidget(child)
        }

        // Close root group
        svgBuilder.append("</g>\n")
        
        svgBuilder.append("</svg>")
        return svgBuilder.toString()
    }
    
    /**
     * Process a composite widget and its children using SVG groups
     */
    private fun processComposite(composite: Composite) {
        val bounds = composite.bounds
        val width = bounds.width
        val height = bounds.height
        
        // Create group for this composite with transform to its position
        val groupId = getNextId()
        svgBuilder.append("""
            <g id="composite_$groupId" transform="translate(${bounds.x}, ${bounds.y})" 
               ${createMetadata("Composite", "", composite)}>
        """.trimIndent())
        
        // Render the composite background using its own coordinate system (0,0)
        renderComposite(composite, 0, 0, width, height)
        
        // Process all children using their relative coordinates within this composite
        for (child in composite.children) {
            processWidget(child)
        }
        
        // Close the group
        svgBuilder.append("</g>\n")
    }
    
    /**
     * Process an individual widget using its SWT relative coordinates
     */
    private fun processWidget(widget: Widget) {
        if (widget !is Control) return
        
        val bounds = widget.bounds
        val x = bounds.x
        val y = bounds.y
        val width = bounds.width
        val height = bounds.height
        
        when (widget) {
            is Button -> renderButton(widget, x, y, width, height)
            is Text -> renderText(widget, x, y, width, height)
            is Label -> renderLabel(widget, x, y, width, height)
            is Composite -> {
                // For nested composites, create a new group
                processComposite(widget)
            }
            else -> renderGenericWidget(widget, x, y, width, height)
        }
    }
    
    /**
     * Render a button widget
     */
    private fun renderButton(button: Button, x: Int, y: Int, width: Int, height: Int) {
        val id = getNextId()
        val buttonType = when {
            (button.style and SWT.CHECK) != 0 -> "checkbox"
            (button.style and SWT.RADIO) != 0 -> "radio"
            else -> "button"
        }
        
        // Button background and border
        svgBuilder.append(createRectElement(
            x = x, y = y, width = width, height = height,
            fill = colorToHex(button.background ?: button.display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND)),
            className = "button-border",
            metadata = createMetadata(buttonType, button.text, button)
        ))
        
        // Button text
        if (button.text.isNotEmpty()) {
            val textX = x + width / 2
            val textY = y + height / 2 + 4 // Approximate vertical center
            
            svgBuilder.append(createTextElement(
                x = textX, y = textY,
                text = button.text,
                className = "widget-text label-text",
                textAnchor = "middle"
            ))
        }
        
        // Special handling for checkboxes and radio buttons
        if (buttonType == "checkbox" || buttonType == "radio") {
            val checkSize = minOf(width, height) - 4
            val checkX = x + 2
            val checkY = y + (height - checkSize) / 2
            
            if (buttonType == "checkbox") {
                svgBuilder.append(createRectElement(
                    x = checkX, y = checkY, width = checkSize, height = checkSize,
                    fill = "white", className = "widget-border"
                ))
                if (button.selection) {
                    // Draw checkmark
                    svgBuilder.append("""
                        <path d="M${checkX + 2},${checkY + checkSize/2} L${checkX + checkSize/2},${checkY + checkSize - 3} L${checkX + checkSize - 2},${checkY + 2}" 
                              stroke="black" stroke-width="2" fill="none"/>
                    """.trimIndent())
                }
            } else {
                // Radio button
                val radius = checkSize / 2
                val centerX = checkX + radius
                val centerY = checkY + radius
                svgBuilder.append("""
                    <circle cx="$centerX" cy="$centerY" r="$radius" fill="white" class="widget-border"/>
                """.trimIndent())
                if (button.selection) {
                    svgBuilder.append("""
                        <circle cx="$centerX" cy="$centerY" r="${radius - 3}" fill="black"/>
                    """.trimIndent())
                }
            }
        }
    }
    
    /**
     * Render a text widget
     */
    private fun renderText(text: Text, x: Int, y: Int, width: Int, height: Int) {
        // Text field background and border
        svgBuilder.append(createRectElement(
            x = x, y = y, width = width, height = height,
            fill = colorToHex(text.background ?: text.display.getSystemColor(SWT.COLOR_LIST_BACKGROUND)),
            className = "text-border",
            metadata = createMetadata("Text", text.text, text)
        ))
        
        // Text content
        if (text.text.isNotEmpty()) {
            val textX = x + 4 // Left padding
            val textY = y + height / 2 + 4 // Approximate vertical center
            
            svgBuilder.append(createTextElement(
                x = textX, y = textY,
                text = text.text,
                className = "widget-text label-text"
            ))
        }
    }
    
    /**
     * Render a label widget
     */
    private fun renderLabel(label: Label, x: Int, y: Int, width: Int, height: Int) {
        // Label background (usually transparent, but render for visibility)
        svgBuilder.append(createRectElement(
            x = x, y = y, width = width, height = height,
            fill = colorToHex(label.background ?: label.display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND)),
            fillOpacity = "0.1",
            className = "widget-border",
            metadata = createMetadata("Label", label.text, label)
        ))
        
        // Label text
        if (label.text.isNotEmpty()) {
            val textX = x + 2
            val textY = y + height / 2 + 4
            
            svgBuilder.append(createTextElement(
                x = textX, y = textY,
                text = label.text,
                className = "widget-text label-text"
            ))
        }
    }
    
    /**
     * Render a composite widget
     */
    private fun renderComposite(composite: Composite, x: Int, y: Int, width: Int, height: Int) {
        svgBuilder.append(createRectElement(
            x = x, y = y, width = width, height = height,
            fill = colorToHex(composite.background ?: composite.display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND)),
            fillOpacity = "0.3",
            className = "widget-border",
            metadata = createMetadata("Composite", "", composite)
        ))
    }
    
    /**
     * Render a generic widget
     */
    private fun renderGenericWidget(widget: Control, x: Int, y: Int, width: Int, height: Int) {
        svgBuilder.append(createRectElement(
            x = x, y = y, width = width, height = height,
            fill = colorToHex(widget.background ?: widget.display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND)),
            fillOpacity = "0.5",
            className = "widget-border",
            metadata = createMetadata(widget.javaClass.simpleName, "", widget)
        ))
    }
    
    /**
     * Create SVG rect element
     */
    private fun createRectElement(
        x: Int, y: Int, width: Int, height: Int,
        fill: String, fillOpacity: String = "1.0",
        className: String = "",
        metadata: String = ""
    ): String {
        val id = getNextId()
        return """
            <rect id="widget_$id" x="$x" y="$y" width="$width" height="$height" 
                  fill="$fill" fill-opacity="$fillOpacity" class="$className" $metadata/>
            
        """.trimIndent()
    }
    
    /**
     * Create SVG text element
     */
    private fun createTextElement(
        x: Int, y: Int, text: String,
        className: String = "", textAnchor: String = "start"
    ): String {
        val id = getNextId()
        val escapedText = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return """
            <text id="text_$id" x="$x" y="$y" class="$className" text-anchor="$textAnchor">$escapedText</text>
            
        """.trimIndent()
    }
    
    /**
     * Create metadata attributes for SVG elements
     */
    private fun createMetadata(type: String, text: String, widget: Widget): String {
        val bounds = if (widget is Control) widget.bounds else Rectangle(0, 0, 0, 0)
        val enabled = if (widget is Control) widget.isEnabled else true
        val visible = if (widget is Control) widget.isVisible else true
        
        return """
            swt:widget-type="$type" 
            swt:widget-text="${text.replace("\"", "&quot;")}" 
            swt:widget-class="${widget.javaClass.name}"
            swt:widget-bounds="${bounds.x},${bounds.y},${bounds.width},${bounds.height}"
            swt:widget-enabled="$enabled"
            swt:widget-visible="$visible"
            swt:x="${bounds.x}"
            swt:y="${bounds.y}"
            swt:width="${bounds.width}"
            swt:height="${bounds.height}"
        """.trimIndent()
    }
    
    /**
     * Convert SWT Color to hex string
     */
    private fun colorToHex(color: Color?): String {
        if (color == null) return "#f0f0f0"
        
        val rgb = color.rgb
        return String.format("#%02x%02x%02x", rgb.red, rgb.green, rgb.blue)
    }
    
    /**
     * Reset generator state
     */
    private fun reset() {
        svgBuilder.clear()
        currentId = 0
    }
    
    /**
     * Get next unique ID
     */
    private fun getNextId(): Int = ++currentId
    
    companion object {
        /**
         * Quick utility to generate SVG from shell
         */
        fun generateFromShell(shell: Shell): String {
            return SWTSvgGenerator().generateSvg(shell)
        }
        
        /**
         * Quick utility to save SVG from shell to file
         */
        fun saveFromShell(shell: Shell, filename: String) {
            SWTSvgGenerator().generateToFile(shell, filename)
        }
    }
}