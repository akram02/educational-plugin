package com.jetbrains.edu.coursecreator.taskDescription

import com.intellij.codeInspection.DefaultXmlSuppressionProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.edu.coursecreator.CCUtils
import com.jetbrains.edu.learning.EduUtils

class ShortcutInTaskDescriptionSuppressionProvider : DefaultXmlSuppressionProvider() {

  override fun isSuppressedFor(element: PsiElement, inspectionId: String): Boolean =
    inspectionId == "CheckDtdRefs" && element.text.startsWith(EduUtils.SHORTCUT_ENTITY)

  override fun isProviderAvailable(file: PsiFile): Boolean =
    CCUtils.isCourseCreator(file.project) && EduUtils.isTaskDescriptionFile(file.name)
}