package com.jetbrains.edu.learning

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.event.DocumentEventImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.edu.coursecreator.yaml.YamlFormatSynchronizer
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder
import com.jetbrains.edu.learning.courseFormat.TaskFile
import com.jetbrains.edu.learning.courseFormat.ext.getVirtualFile
import com.jetbrains.edu.learning.editor.EduSingleFileEditor

/**
 * Listens changes in study files and updates
 * coordinates of all the placeholders in current task file
 */
class EduDocumentListener(
  private val taskFile: TaskFile,
  private val updateYaml: Boolean
) : DocumentListener {

  override fun beforeDocumentChange(e: DocumentEvent) {
    if (!taskFile.isTrackChanges) {
      return
    }
    taskFile.isHighlightErrors = true
  }

  override fun documentChanged(e: DocumentEvent) {
    if (!taskFile.isTrackChanges) {
      return
    }
    if (taskFile.answerPlaceholders.isEmpty()) return

    if (e !is DocumentEventImpl) {
      return
    }

    val offset = e.getOffset()
    val change = e.getNewLength() - e.getOldLength()

    val fragment = e.getNewFragment()
    val oldFragment = e.getOldFragment()

    for (placeholder in taskFile.answerPlaceholders) {
      var placeholderStart = placeholder.offset
      var placeholderEnd = placeholder.endOffset

      val changes = getChangeForOffsets(offset, change, placeholder)
      val changeForStartOffset = changes.getFirst()
      val changeForEndOffset = changes.getSecond()

      placeholderStart += changeForStartOffset
      placeholderEnd += changeForEndOffset

      if (placeholderStart - 1 == offset && fragment.toString().isEmpty() && oldFragment.toString().startsWith("\n")) {
        placeholderStart -= 1
      }

      if (placeholderStart == offset && oldFragment.toString().isEmpty() && fragment.toString().startsWith("\n")) {
        placeholderStart += 1
      }

      val length = placeholderEnd - placeholderStart
      assert(length >= 0)
      assert(placeholderStart >= 0)
      updatePlaceholder(placeholder, placeholderStart, length)
    }
  }

  private fun getChangeForOffsets(offset: Int, change: Int, placeholder: AnswerPlaceholder): Pair<Int, Int> {
    val placeholderStart = placeholder.offset
    val placeholderEnd = placeholder.endOffset
    var start = 0
    var end = change
    if (offset > placeholderEnd) {
      return Pair.create(0, 0)
    }

    if (offset < placeholderStart) {
      start = change

      if (change < 0 && offset - change > placeholderStart) {  // delete part of placeholder start
        start = offset - placeholderStart
      }
    }

    if (change < 0 && offset - change > placeholderEnd) {   // delete part of placeholder end
      end = offset - placeholderEnd
    }

    return Pair.create(start, end)
  }

  private fun updatePlaceholder(answerPlaceholder: AnswerPlaceholder, start: Int, length: Int) {
    answerPlaceholder.offset = start
    answerPlaceholder.length = length
    if (updateYaml) {
      YamlFormatSynchronizer.saveItem(taskFile.task)
    }
  }

  companion object {
    @JvmOverloads
    @JvmStatic
    fun runWithListener(
      project: Project,
      taskFile: TaskFile,
      updateYaml: Boolean,
      file: VirtualFile? = taskFile.getVirtualFile(project),
      action: (Document) -> Unit
    ) {
      if (file == null) return
      val document = FileDocumentManager.getInstance().getDocument(file) ?: return
      // EduSingleFileEditor adds own EduDocumentListener on creation
      val hasListener = FileEditorManager.getInstance(project).getAllEditors(file).any { it is EduSingleFileEditor }
      val eduDocumentListener = if (!hasListener) EduDocumentListener(taskFile, updateYaml) else null
      if (eduDocumentListener != null) {
        document.addDocumentListener(eduDocumentListener)
      }
      try {
        action(document)
      }
      finally {
        if (eduDocumentListener != null) {
          document.removeDocumentListener(eduDocumentListener)
        }
      }
    }
  }
}