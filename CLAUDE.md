# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AutoSWT is a lightweight, JUnit-integrated testing framework designed specifically for testing SWT-based UIs. It provides thread-safe test automation, automatic display management, and comprehensive debugging tools with a fluent layout assertion API.

## Key Commands

### Building and Testing
```bash
# Build the project
./gradlew build

# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "AutoSWTExtensionsTest"

# Run tests with debug output
./gradlew test --info

# Clean and rebuild
./gradlew clean build
```

### Test Management
- Tests use JUnit Jupiter framework
- All test classes are in `src/test/kotlin/`
- Test screenshots and SVG outputs are saved to `./build/test-screenshots/`

## Architecture

### Core Components

1. **AutoSWT** (`src/main/kotlin/io/github/ddsimoes/autoswt/AutoSWT.kt`)
   - Main testing framework with thread-safe SWT operations
   - Automatic display management with TestDisplay
   - Widget finding, interaction, and state checking
   - Comprehensive layout assertion API
   - SVG generation and debugging tools

2. **SWTSvgGenerator** (`src/main/kotlin/io/github/ddsimoes/autoswt/SWTSvgGenerator.kt`)
   - Creates visual SVG representations of SWT UI hierarchies
   - Includes widget metadata and positioning information
   - Supports debugging and visual verification

### Test Structure Pattern

All SWT tests follow this pattern:
```kotlin
@Test
fun myTest() {
    autoSWT {
        testShell(width = 400, height = 300) { shell ->
            // UI setup here
        }.test { shell ->
            // Test logic here with full AutoSWT API access
        }
        // Automatic cleanup happens here
    }
}
```

### Key Features

- **Thread-Safe Operations**: All widget finding and interaction methods automatically handle SWT's single-threaded nature
- **Automatic Resource Management**: TestDisplay and shell lifecycle are managed automatically
- **Layout Assertion API**: Fluent API for testing UI positioning, sizing, and alignment with exact precision
- **Widget Finding**: Type-safe widget finding with predicates: `shell.find<Button> { it.text == "Submit" }`
- **Event Simulation**: Real SWT event generation for buttons, text fields, keyboard, and mouse interactions
- **Visual Debugging**: SVG generation and screenshot capabilities for test verification

### Layout Testing Philosophy

- **Strict by default**: Use tolerance = 0 for most assertions to catch real layout bugs
- **Fluent API**: Chain assertions for readability: `button.assertLayout().isVisible().isRightOf(label, gap = 8)`
- **Bulk operations**: Test multiple widgets together: `buttons.assertLayout().areArrangedInRow(minGap = 5)`
- **Relationship-focused**: Test logical relationships between widgets rather than absolute coordinates

## Development Guidelines

### Widget Testing Best Practices

1. **Use `autoSWT` for all SWT tests** - ensures proper setup and cleanup
2. **Let AutoSWT handle thread safety** - avoid manual `runOnSWT` for find/interact operations
3. **Use layout assertion API** - replace manual `visibleBounds()` calculations
4. **Test layout patterns, not coordinates** - focus on relationships between widgets
5. **Generate SVGs for debugging** - `shell.saveSVG("debug.svg")` for visual verification

### Common Patterns

```kotlin
// Widget finding (thread-safe by default)
val button = shell.find<Button> { it.text == "Submit" }
val textFields = shell.findAll<Text> { true }

// Widget interactions (thread-safe by default)  
textField.typeText("Hello World")
button.doSelect()

// Layout assertions (fluent API)
button.assertLayout()
    .isVisible()
    .hasMinSize(width = 100, height = 30)
    .isRightOf(label, gap = 8)
    .isWithin(container)

// Bulk layout testing
listOf(button1, button2, button3).assertLayout()
    .areAllVisible()
    .areArrangedInRow(minGap = 8)
    .fitWithin(toolbar)

// Manual thread safety (only when needed)
val text = runOnSWT { label.text }
```

### Error Handling

- Layout assertions provide detailed error messages with actual vs expected values
- All AutoSWT operations include proper error context
- Test failures include widget bounds and positioning information

### Platform Support

- Cross-platform SWT support (Windows, Linux, macOS)
- Platform-specific SWT dependencies resolved automatically via Gradle configuration
- Kotlin official code style enforced via `gradle.properties`

## File Structure

- `src/main/kotlin/` - Main AutoSWT framework code
- `src/test/kotlin/` - Test examples and framework tests
- `build.gradle.kts` - Gradle build configuration with SWT platform resolution
- `build/test-screenshots/` - Generated test artifacts (SVGs, screenshots)