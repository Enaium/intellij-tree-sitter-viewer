/*
 * Copyright (c) 2026 Enaium
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package cn.enaium.treesitter.viewer

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.readText
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.treeStructure.Tree
import org.treesitter.*
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTree
import javax.swing.Timer
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath


class TreeSitterViewer : ToolWindowFactory {
    override fun createToolWindowContent(
        project: Project,
        window: ToolWindow
    ) {
        window.component.add(Viewer(project))
    }

    class Viewer(val project: Project) : JPanel(BorderLayout()), Disposable {
        private val tree = Tree()
        private var currentFile: VirtualFile? = null
        private var currentTree: TSTree? = null
        private var currentLanguage: TSLanguage? = null
        private var listenedEditor: com.intellij.openapi.editor.Editor? = null
        private var caretListener: CaretListener? = null
        private var suppressCaretSync = false

        private val queryTextArea = JBTextArea(4, 0).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            lineWrap = true
            wrapStyleWord = true
        }
        private val queryScrollPane = JBScrollPane(queryTextArea).apply {
            border = BorderFactory.createTitledBorder("Query")
        }
        private val queryHighlights = mutableListOf<RangeHighlighter>()
        private var queryTimer: Timer? = null
        private var highlightedEditor: com.intellij.openapi.editor.Editor? = null

        init {
            add(queryScrollPane, BorderLayout.NORTH)
            add(JScrollPane(tree), BorderLayout.CENTER)
            tree.toggleClickCount = Int.MAX_VALUE
            val renderer = TypeColorRenderer()
            renderer.leafIcon = null
            renderer.openIcon = null
            renderer.closedIcon = null
            tree.cellRenderer = renderer

            queryTextArea.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = scheduleQuery()
                override fun removeUpdate(e: DocumentEvent?) = scheduleQuery()
                override fun changedUpdate(e: DocumentEvent?) = scheduleQuery()
            })

            setupListeners()
            refresh()
        }

        private fun setupListeners() {
            val connection = project.messageBus.connect(this)
            connection.subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                object : FileEditorManagerListener {
                    override fun selectionChanged(event: FileEditorManagerEvent) {
                        refresh()
                    }
                }
            )

            connection.subscribe(
                VirtualFileManager.VFS_CHANGES,
                object : BulkFileListener {
                    override fun after(events: MutableList<out VFileEvent>) {
                        val file = currentFile ?: return
                        if (events.any { it.path == file.path }) {
                            refresh()
                        }
                    }
                }
            )

            tree.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2 && e.button == MouseEvent.BUTTON1) {
                        val treePath = tree.getPathForLocation(e.x, e.y) ?: return
                        val treeNode =
                            treePath.lastPathComponent as? DefaultMutableTreeNode ?: return
                        navigateToEditor((treeNode.userObject as NodeWrapper).node)
                    }
                }
            })
        }

        private fun registerCaretListener() {
            val editor =
                FileEditorManager.getInstance(project).selectedTextEditor ?: return
            if (editor == listenedEditor) return

            if (caretListener != null && listenedEditor != null) {
                listenedEditor!!.caretModel.removeCaretListener(caretListener!!)
            }

            val listener = object : CaretListener {
                override fun caretPositionChanged(event: CaretEvent) {
                    if (suppressCaretSync) return
                    val position = event.newPosition ?: return
                    val model = tree.model ?: return
                    val root = model.root as? DefaultMutableTreeNode ?: return
                    val node = currentTree?.rootNode?.getNamedDescendantForPointRange(
                        TSPoint(position.line, position.column),
                        TSPoint(position.line, position.column)
                    ) ?: return
                    selectTreeNode(root, node)
                }
            }
            editor.caretModel.addCaretListener(listener)
            caretListener = listener
            listenedEditor = editor
        }

        private fun selectTreeNode(
            root: DefaultMutableTreeNode,
            target: TSNode
        ) {
            val path = mutableListOf<DefaultMutableTreeNode>(root)

            while (true) {
                val parent = path.last()
                var found = false
                for (i in 0 until parent.childCount) {
                    val child = parent.getChildAt(i) as? DefaultMutableTreeNode ?: continue
                    if (containsNode(target, child)) {
                        path.add(child)
                        found = true
                        break
                    }
                }
                if (!found) break
            }

            val treePath = TreePath(path.toTypedArray())
            tree.selectionPath = treePath
            tree.scrollPathToVisible(treePath)
        }

        private fun containsNode(
            target: TSNode,
            treeNode: DefaultMutableTreeNode
        ): Boolean {
            val wrapper = treeNode.userObject as? NodeWrapper ?: return false
            val node = wrapper.node
            val startOk = node.startPoint.row < target.startPoint.row ||
                    (node.startPoint.row == target.startPoint.row && node.startPoint.column <= target.startPoint.column)
            val endOk = node.endPoint.row > target.endPoint.row ||
                    (node.endPoint.row == target.endPoint.row && node.endPoint.column >= target.endPoint.column)
            return startOk && endOk
        }

        private fun navigateToEditor(node: TSNode) {
            val editor =
                FileEditorManager.getInstance(project).selectedTextEditor ?: return
            val start = node.startPoint
            suppressCaretSync = true
            editor.caretModel.moveToLogicalPosition(
                LogicalPosition(start.row, start.column)
            )
            editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
            suppressCaretSync = false
        }

        private fun scheduleQuery() {
            queryTimer?.stop()
            queryTimer = Timer(300) { executeQuery() }
            queryTimer?.isRepeats = false
            queryTimer?.start()
        }

        private fun executeQuery() {
            clearHighlights()

            val queryText = queryTextArea.text.trim()
            if (queryText.isEmpty()) return

            val tsTree = currentTree ?: return
            val lang = currentLanguage ?: return
            val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return

            try {
                TSQuery(lang, queryText).use { query ->
                    TSQueryCursor().use { cursor ->
                        cursor.exec(query, tsTree.rootNode)

                        highlightedEditor = editor
                        val match = TSQueryMatch()
                        while (cursor.nextMatch(match)) {
                            for (capture in match.captures) {
                                val captureName = query.getCaptureNameForId(capture.index)
                                val node = capture.node
                                val startOffset = editor.logicalPositionToOffset(
                                    LogicalPosition(node.startPoint.row, node.startPoint.column)
                                )
                                val endOffset = editor.logicalPositionToOffset(
                                    LogicalPosition(node.endPoint.row, node.endPoint.column)
                                )

                                if (startOffset < 0 || endOffset > editor.document.textLength || startOffset >= endOffset) continue

                                val highlighter = editor.markupModel.addRangeHighlighter(
                                    startOffset, endOffset,
                                    HighlighterLayer.ADDITIONAL_SYNTAX,
                                    TextAttributes().apply {
                                        backgroundColor = colorForCapture(captureName)
                                    },
                                    HighlighterTargetArea.EXACT_RANGE
                                )
                                queryHighlights.add(highlighter)
                            }
                        }
                    }
                }
            } catch (_: TSQueryException) {
                // Invalid query, skip
            }
        }

        companion object {
            private val captureColors = mutableMapOf<String, Color>()

            private fun colorForCapture(name: String): Color {
                return captureColors.getOrPut(name) {
                    val hue = (name.hashCode() and 0x7FFFFFFF) % 360
                    val rgb = Color.getHSBColor(hue / 360f, 0.6f, 0.95f)
                    Color(rgb.red, rgb.green, rgb.blue, 60)
                }
            }
        }

        private fun clearHighlights() {
            val editor = highlightedEditor ?: return
            for (highlighter in queryHighlights) {
                editor.markupModel.removeHighlighter(highlighter)
            }
            queryHighlights.clear()
            highlightedEditor = null
        }

        private fun refresh() {
            clearHighlights()

            val content =
                FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
            currentFile = content

            if (content == null) {
                tree.model = null
                currentTree = null
                currentLanguage = null
                queryScrollPane.isVisible = false
                return
            }

            queryScrollPane.isVisible = true

            val parser = TSParser()
            val language = when (content.extension) {
                "agda" -> TreeSitterAgda()
                "bash", "sh", "zsh" -> TreeSitterBash()
                "c" -> TreeSitterC()
                "cs", "csharp" -> TreeSitterCSharp()
                "cpp", "cc", "cxx", "hpp", "hxx", "hh" -> TreeSitterCpp()
                "css" -> TreeSitterCss()
                "erb" -> TreeSitterEmbeddedTemplate()
                "go" -> TreeSitterGo()
                "hs", "lhs" -> TreeSitterHaskell()
                "html", "htm" -> TreeSitterHtml()
                "java" -> TreeSitterJava()
                "js", "mjs", "cjs" -> TreeSitterJavascript()
                "json" -> TreeSitterJson()
                "jl" -> TreeSitterJulia()
                "kt", "kts", "ktm" -> TreeSitterKotlin()
                "ml", "mli" -> TreeSitterOcaml()
                "php" -> TreeSitterPhp()
                "py" -> TreeSitterPython()
                "re" -> TreeSitterRegex()
                "re2c" -> TreeSitterRe2c()
                "rb" -> TreeSitterRuby()
                "rs" -> TreeSitterRust()
                "scala", "sc" -> TreeSitterScala()
                "tsx" -> TreeSitterTsx()
                "ts", "mts", "cts" -> TreeSitterTypescript()
                "v" -> TreeSitterVerilog()
                "gql", "graphql" -> TreeSitterGraphql()
                "hack" -> TreeSitterHack()
                "hcl" -> TreeSitterHcl()
                "hocon" -> TreeSitterHocon()
                "jq" -> TreeSitterJq()
                "json5" -> TreeSitterJson5()
                "lalrpop" -> TreeSitterLalrpop()
                "tex", "sty", "cls", "bst", "ltx" -> TreeSitterLatex()
                "lean" -> TreeSitterLean()
                "ll" -> TreeSitterLlvm()
                "mir" -> TreeSitterLlvmMir()
                "m68k" -> TreeSitterM68k()
                "lua" -> TreeSitterLua()
                "mk" -> TreeSitterMake()
                "md", "markdown" -> TreeSitterMarkdown()
                "nginx" -> TreeSitterNginx()
                "nim", "nims" -> TreeSitterNim()
                "nix" -> TreeSitterNix()
                "m", "mm" -> TreeSitterObjc()
                "ohm" -> TreeSitterOhm()
                "org" -> TreeSitterOrg()
                "p4" -> TreeSitterP4()
                "pas", "pp", "inc" -> TreeSitterPascal()
                "pl", "pm" -> TreeSitterPerl()
                "pod" -> TreeSitterPod()
                "pgn" -> TreeSitterPgn()
                "proto" -> TreeSitterProto()
                "qml" -> TreeSitterQmljs()
                "r" -> TreeSitterR()
                "rkt", "rktd", "rktl" -> TreeSitterRacket()
                "rasi" -> TreeSitterRasi()
                "rego" -> TreeSitterRego()
                "rst" -> TreeSitterRst()
                "scm", "ss" -> TreeSitterScheme()
                "scss" -> TreeSitterScss()
                "sexp" -> TreeSitterSexp()
                "smali" -> TreeSitterSmali()
                "sparql", "rq" -> TreeSitterSparql()
                "sql" -> TreeSitterSql()
                "bq", "bigquery" -> TreeSitterSqlBigquery()
                "sqlite" -> TreeSitterSqlite()
                "ssh-config" -> TreeSitterSshClientConfig()
                "query" -> TreeSitterQuery()
                "sp", "sma" -> TreeSitterSourcepawn()
                "meson.build" -> TreeSitterMeson()
                "svelte" -> TreeSitterSvelte()
                "swift" -> TreeSitterSwift()
                "td" -> TreeSitterTablegen()
                "tact" -> TreeSitterTact()
                "thrift" -> TreeSitterThrift()
                "toml" -> TreeSitterToml()
                "ttl" -> TreeSitterTurtle()
                "twig" -> TreeSitterTwig()
                "vhd", "vhdl" -> TreeSitterVhdl()
                "vue" -> TreeSitterVue()
                "wast" -> TreeSitterWast()
                "wat" -> TreeSitterWat()
                "wgsl" -> TreeSitterWgsl()
                "yaml", "yml" -> TreeSitterYaml()
                "yang" -> TreeSitterYang()
                "zig" -> TreeSitterZig()
                else -> {
                    tree.model = null
                    currentTree = null
                    currentLanguage = null
                    queryScrollPane.isVisible = false
                    return
                }
            }
            parser.setLanguage(language)
            currentLanguage = language

            try {
                val parseString = parser.parseString(null, content.readText())
                currentTree = parseString
                val root = parseString.rootNode
                tree.model = DefaultTreeModel(buildTreeNode(root))
                expandNodes(0)
            } catch (e: Exception) {
                tree.model = null
                currentTree = null
            }

            registerCaretListener()
            executeQuery()
        }

        private fun expandNodes(depth: Int) {
            if (depth > 4) return
            var row = 0
            while (row < tree.rowCount) {
                tree.expandRow(row)
                row++
            }
        }

        private fun buildTreeNode(node: TSNode, fieldName: String? = null): DefaultMutableTreeNode {
            val treeNode = DefaultMutableTreeNode(NodeWrapper(node, fieldName))
            for (i in 0 until node.namedChildCount) {
                treeNode.add(buildTreeNode(node.getNamedChild(i), node.getFieldNameForNamedChild(i)))
            }
            return treeNode
        }

        override fun dispose() {
            val editor = listenedEditor
            val listener = caretListener
            if (editor != null && listener != null) {
                editor.caretModel.removeCaretListener(listener)
            }
            queryTimer?.stop()
            clearHighlights()
        }
    }

    class NodeWrapper(val node: TSNode, val fieldName: String? = null) {
        override fun toString(): String {
            val prefix = if (fieldName != null) "$fieldName: " else ""
            return "${prefix}${node.type} [${node.startPoint.row}, ${node.startPoint.column}] - [${node.endPoint.row}, ${node.endPoint.column}]"
        }
    }

    private class TypeColorRenderer : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(
            tree: JTree,
            value: Any,
            sel: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ): Component {
            val component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus) as JLabel
            if (sel) return component
            val wrapper = (value as? DefaultMutableTreeNode)?.userObject as? NodeWrapper ?: return component
            val text = value.toString()
            val type = wrapper.node.type
            val typeIndex = if (wrapper.fieldName != null) text.indexOf(type, wrapper.fieldName.length + 2) else text.indexOf(type)
            if (typeIndex >= 0) {
                val before = text.substring(0, typeIndex)
                val after = text.substring(typeIndex + type.length)
                component.text = "<html>$before<span style='color:#4682B4'>$type</span>$after</html>"
            }
            return component
        }
    }
}
