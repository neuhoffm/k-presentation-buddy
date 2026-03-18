# K Presentation Buddy

[![JetBrains Plugins](https://img.shields.io/jetbrains/plugin/v/30567-k-presentation-budd.svg)](https://plugins.jetbrains.com/plugin/30567-k-presentation-budd)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/30567-k-presentation-budd.svg)](https://plugins.jetbrains.com/plugin/30567-k-presentation-budd)

**This project was inspired by [Presentation Buddy](https://github.com/mauricedb/presentation-buddy) and adapted for
IntelliJ IDEs**

## Features

Automatically type code during presentations in IntelliJ based IDEs

## Menu Functions

All functionality is controlled through the Presentation Buddy menu items in the **Tools** menu.

### Load Instructions File

Allows you to select which instructions JSON file you want to load.
Instruction options are listed below.
The instructions file consists of a simple array of one or more instructions.

### Start Demo

Begins the execution of the demo, the hotkey currently assigned is CTRL + ALT + 1
but can be reassigned in Settings -> Keymap and searching for `start demo`.

**In order for this command to be available an instruction file needs to be loaded and an editor window opened.**

### Pause Demo

Pauses the execution of the demo. The hotkey is mapped to the same assignment as Start Demo.

### Resume Demo

Resumes the execution of the demo. The hotkey is mapped to the same assignment as Start Demo.

## Instructions

K Presentation Buddy supports the following instruction types:

### TypeText

Type the specified text at the current cursor position of the currently open file.
The text setting can either be a string or an array of strings.
In the case of an array of strings each string is entered on a new line.

| Property | Required? | Example Value                      |
|----------|-----------|------------------------------------|
| type     | Yes       | `"typeText"`                       |
| text     | Yes       | `"package"` or `["data", "class"]` |
| delay    | No        | `100`                              |

Example:

```json
{
  "type": "typeText",
  "text": [
    "package org.example",
    "",
    "data class ClassesModel(val id: String, val name: String)"
  ],
  "delay": 200
}
```

---

### TypeTextFromFile

Type the entire contents of a file at once at the current cursor position.
The file is read from a path relative to the instructions file.

| Property | Required? | Example Value        |
|----------|-----------|----------------------|
| type     | Yes       | `"typeTextFromFile"` |
| path     | Yes       | `"Models.kt"`        |
| delay    | No        | `100`                |

Example:

```json
{
  "type": "typeTextFromFile",
  "path": "Models.kt",
  "delay": 100
}
```

---

### TypeChunksFromFile

Type text from a file line-by-line with advanced pause control.
Read from a path relative to the instructions file.

K Presentation Buddy will:

* pause after every typed line when `waitAfterNewLine` is `true`
* pause after typing any line containing a value from `waitAfterTyping`
* pause instead of typing any line exactly matching a value from `waitInsteadOfTyping`
* skip any line containing any value from `skipLinesContaining`

| Property            | Required? | Example Value                        |
|---------------------|-----------|--------------------------------------|
| type                | Yes       | `"typeChunksFromFile"`              |
| path                | Yes       | `"Models.kt"`                       |
| waitAfterNewLine    | No        | `true`                               |
| waitAfterTyping     | No        | `["class", "fun main"]`             |
| waitInsteadOfTyping | No        | `["// STOP HERE"]`                  |
| skipLinesContaining | No        | `["TODO", "@Generated"]`            |
| delay               | No        | `100`                                |

Example:

```json
{
  "type": "typeChunksFromFile",
  "path": "Models.kt",
  "waitAfterNewLine": false,
  "waitAfterTyping": ["class"],
  "waitInsteadOfTyping": ["// STOP HERE"],
  "skipLinesContaining": ["TODO"],
  "delay": 120
}
```

---

### CreateFile

Create a new file in the project with optional initial content.

| Property      | Required? | Example Value         |
|---------------|-----------|----------------------|
| type          | Yes       | `"createFile"`        |
| path          | Yes       | `"src/Main.kt"`       |
| text          | No        | `"initial content"`   |
| openInEditor  | No        | `true`                |

Example:

```json
{
  "type": "createFile",
  "path": "src/Main.kt",
  "text": "fun main() {\n    println(\"Hello, World!\")\n}",
  "openInEditor": true
}
```

---

### OpenFile

Open an existing file in the editor.
The file path is relative to the project root.

| Property | Required? | Example Value |
|----------|-----------|---------------|
| type     | Yes       | `"openFile"`  |
| path     | Yes       | `"src/Main.kt"` |

Example:

```json
{
  "type": "openFile",
  "path": "src/Main.kt"
}
```

---

### OpenFolder

Open and select a folder in the project view.
The folder path is relative to the project root.

| Property | Required? | Example Value    |
|----------|-----------|------------------|
| type     | Yes       | `"openFolder"`   |
| path     | Yes       | `"src/main/kotlin"` |

Example:

```json
{
  "type": "openFolder",
  "path": "src/main/kotlin"
}
```

---

### GoTo

Move the cursor to a specific line and column in the current editor.

| Property | Required? | Example Value |
|----------|-----------|---------------|
| type     | Yes       | `"goto"`      |
| line     | Yes       | `10`          |
| column   | Yes       | `5`           |

Example:

```json
{
  "type": "goto",
  "line": 10,
  "column": 5
}
```

---

### Select

Select a range of text in the editor.
Line and column numbers are 1-based.
Optionally save the selection by name for later use with Delete instruction.

| Property | Required? | Example Value |
|----------|-----------|---------------|
| type     | Yes       | `"select"`    |
| start    | Yes       | `{"line": 10, "column": 1}` |
| end      | Yes       | `{"line": 15, "column": 20}` |
| name     | No        | `"mySelection"` |

Example:

```json
{
  "type": "select",
  "start": {"line": 10, "column": 1},
  "end": {"line": 15, "column": 20},
  "name": "mySelection"
}
```

---

### Delete

Delete a range of characters or a previously named selection.

| Property   | Required? | Example Value |
|-----------|-----------|---------------|
| type      | Yes       | `"delete"`    |
| characters | No        | `5`           |
| selection | No        | `"mySelection"` |

Either `characters` (deletes N chars from cursor) or `selection` (deletes named selection) should be specified.

Example (delete by character count):

```json
{
  "type": "delete",
  "characters": 5
}
```

Example (delete by selection):

```json
{
  "type": "delete",
  "selection": "mySelection"
}
```

---

### Replace

Replace the current selection with new text.

| Property | Required? | Example Value         |
|----------|-----------|----------------------|
| type     | Yes       | `"replace"`           |
| text     | Yes       | `"new text"` or `["line1", "line2"]` |

Example:

```json
{
  "type": "replace",
  "text": "new implementation"
}
```

---

### Command

Execute any IntelliJ IDE action/command.

| Property | Required? | Example Value      |
|----------|-----------|-------------------|
| type     | Yes       | `"command"`       |
| command  | Yes       | `"EditorSelectWord"` |

Example:

```json
{
  "type": "command",
  "command": "EditorSelectWord"
}
```

A reference of IDE commands can be found here: [IntelliJ-Action-IDs](https://github.com/centic9/IntelliJ-Action-IDs/blob/master/docs/_data/actions.csv)

---

### RunInTerminal

Execute a command in the IDE's terminal.
Supports both typing the command and executing it.

| Property | Required? | Example Value |
|----------|-----------|---------------|
| type     | Yes       | `"runInTerminal"` |
| command  | Yes       | `"gradle build"` |
| execute  | No        | `true`        |
| delay    | No        | `100`         |

Example:

```json
{
  "type": "runInTerminal",
  "command": "gradle build",
  "execute": true,
  "delay": 100
}
```

---

### Wait

Pause the presentation and wait for user input to resume.
This is a simple instruction with no properties.

| Property | Value |
|----------|-------|
| type     | `"wait"` |

Example:

```json
{
  "type": "wait"
}
```

---

## Complete Example

Here's a complete demo file using multiple instruction types:

```json
[
  {
    "type": "createFile",
    "path": "src/Main.kt",
    "openInEditor": true
  },
  {
    "type": "typeText",
    "text": "fun main() {",
    "delay": 100
  },
  {
    "type": "typeText",
    "text": "    println(\"Hello, World!\")",
    "delay": 100
  },
  {
    "type": "typeText",
    "text": "}",
    "delay": 100
  },
  {
    "type": "wait"
  },
  {
    "type": "command",
    "command": "ReformatCode"
  },
  {
    "type": "runInTerminal",
    "command": "gradle run",
    "execute": true
  }
]
```

