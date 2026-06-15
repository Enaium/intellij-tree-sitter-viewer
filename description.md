# TreeSitterViewer

A powerful and intuitive IntelliJ IDEA plugin that visualizes the abstract syntax tree (AST) of your source code in real-time using [Tree-sitter](https://tree-sitter.github.io/tree-sitter/).

## Features

- **Live Syntax Tree** - Automatically parses and displays the AST of the currently active file in a docked tool window (right panel).
- **90+ Supported Languages** - Includes parsers for virtually every major language: Java, Kotlin, Python, JavaScript/TypeScript, Go, Rust, C/C++, C#, Ruby, Swift, PHP, HTML/CSS, SQL, Lua, Zig, and many more.
- **Bidirectional Navigation**
  - *Editor to Tree* - Move your caret in the editor and the corresponding tree node is automatically selected and scrolled into view.
  - *Tree to Editor* - Double-click any tree node to jump directly to its source location in the editor.
- **Field Names & Ranges** - Each node displays its grammar field name (when applicable), node type, and the exact source range.
- **Color-Coded Types** - Node types are highlighted in a distinct color for easy visual scanning.
- **Live Refresh** - The tree updates automatically when you edit the file or switch between open files.
