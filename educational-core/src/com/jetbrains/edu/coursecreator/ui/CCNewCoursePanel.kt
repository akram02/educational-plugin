package com.jetbrains.edu.coursecreator.ui

import com.intellij.ide.impl.ProjectUtil
import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.layout.*
import com.intellij.util.text.nullize
import com.intellij.util.ui.JBUI
import com.jetbrains.edu.coursecreator.CCUtils
import com.jetbrains.edu.coursecreator.getDefaultCourseType
import com.jetbrains.edu.learning.EduNames
import com.jetbrains.edu.learning.LanguageSettings
import com.jetbrains.edu.learning.configuration.EduConfigurator
import com.jetbrains.edu.learning.configuration.EduConfiguratorManager
import com.jetbrains.edu.learning.configuration.EducationalExtensionPoint
import com.jetbrains.edu.learning.courseFormat.Course
import com.jetbrains.edu.learning.courseFormat.EduCourse
import com.jetbrains.edu.learning.enablePlugins
import com.jetbrains.edu.learning.getDisabledPlugins
import com.jetbrains.edu.learning.newproject.ui.ErrorComponent
import com.jetbrains.edu.learning.newproject.ui.ErrorState
import com.jetbrains.edu.learning.newproject.ui.ValidationMessage
import com.jetbrains.edu.learning.newproject.ui.courseSettings.CourseSettings
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ItemEvent
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import javax.swing.text.AttributeSet
import javax.swing.text.PlainDocument

class CCNewCoursePanel(course: Course? = null, courseProducer: () -> Course = ::EduCourse) : JPanel() {

  private val myPanel: JPanel
  private val myCourseDataComboBox: ComboBox<CourseData> = ComboBox()
  private val myTitleField: CourseTitleField = CourseTitleField()
  private val myDescriptionTextArea: JTextArea = JTextArea()

  private val myAdvancedSettings: CourseSettings = CourseSettings()
  private val myPathField: PathField = PathField()
  private val myLocationField: LabeledComponent<TextFieldWithBrowseButton> = createLocationField()

  private val myErrorComponent = ErrorComponent(getHyperlinkListener())

  private val myCourse: Course = (course ?: courseProducer()).apply { courseMode = CCUtils.COURSE_MODE }
  private lateinit var myLanguageSettings: LanguageSettings<*>

  private var myRequiredAndDisabledPlugins: List<String> = emptyList()

  private var myValidationListener: ValidationListener? = null

  private val context: UserDataHolder = UserDataHolderBase()

  init {
    layout = BorderLayout()
    // Check both Darcula and Light theme before changing size
    preferredSize = JBUI.size(700, 372)
    minimumSize = JBUI.size(700, 372)

    myDescriptionTextArea.rows = 10
    myDescriptionTextArea.lineWrap = true
    myDescriptionTextArea.wrapStyleWord = true

    val scrollPane = JBScrollPane(myDescriptionTextArea)
    myPanel = panel {
      row("Title:") { myTitleField(CCFlags.pushX) }
      row("Type:") { myCourseDataComboBox(CCFlags.growX) }
      row("Description:") { scrollPane(CCFlags.growX) }
    }

    myTitleField.document = CourseTitleDocument()
    myTitleField.complementaryTextField = myPathField
    myPathField.complementaryTextField = myTitleField

    val bottomPanel = JPanel(BorderLayout())
    myErrorComponent.minimumSize = JBUI.size(700, 34)
    myErrorComponent.preferredSize = myErrorComponent.minimumSize
    myErrorComponent.border = JBUI.Borders.empty(5, 6, 2, 2)
    bottomPanel.add(myErrorComponent, BorderLayout.SOUTH)
    bottomPanel.add(myAdvancedSettings, BorderLayout.NORTH)

    add(myPanel, BorderLayout.NORTH)
    add(bottomPanel, BorderLayout.SOUTH)

    myCourseDataComboBox.renderer = object : DefaultListCellRenderer() {
      override fun getListCellRendererComponent(list: JList<*>,
                                                value: Any?,
                                                index: Int,
                                                isSelected: Boolean,
                                                cellHasFocus: Boolean): Component {
        val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        if (component is JLabel && value is CourseData) {
          component.text = value.displayName
          component.icon = value.icon
        }
        return component
      }
    }

    val courseData = collectCoursesData(course)
    courseData.forEach { myCourseDataComboBox.addItem(it) }

    val defaultCourseType = getDefaultCourseType(courseData)
    if (defaultCourseType != null) {
      myCourseDataComboBox.selectedItem = defaultCourseType
      onCourseDataSelected(defaultCourseType)
    }

    myCourseDataComboBox.addItemListener {
      if (it.stateChange == ItemEvent.SELECTED) {
        onCourseDataSelected(it.item as CourseData)
      }
    }
    setupValidation()

    if (course != null) {
      myDescriptionTextArea.text = course.description
      myTitleField.setTextManually(course.name)
      myCourseDataComboBox.isEnabled = false
    }
  }

  val course: Course
    get() {
      myCourse.name = myTitleField.text
      myCourse.description = myDescriptionTextArea.text
      return myCourse
    }
  val projectSettings: Any get() = myLanguageSettings.settings
  val locationString: String get() = myLocationField.component.text

  fun setValidationListener(listener: ValidationListener?) {
    myValidationListener = listener
    doValidation()
  }

  private fun setupValidation() {
    val validator = object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        doValidation()
      }
    }

    myTitleField.document.addDocumentListener(validator)
    myDescriptionTextArea.document.addDocumentListener(validator)
    myLocationField.component.textField.document.addDocumentListener(validator)
  }

  private fun getHyperlinkListener(): HyperlinkListener = HyperlinkListener { e ->
    if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
      enablePlugins(myRequiredAndDisabledPlugins)
    }
  }

  private fun doValidation() {
    val validationMessage = when {
      myTitleField.text.isNullOrBlank() -> ValidationMessage("Enter course title")
      myDescriptionTextArea.text.isNullOrBlank() -> ValidationMessage("Enter course description")
      locationString.isBlank() -> ValidationMessage("Enter course location")
      !FileUtil.ensureCanCreateFile(File(FileUtil.toSystemDependentName(locationString))) -> ValidationMessage(
        "Can't create course at this location")
      myRequiredAndDisabledPlugins.isNotEmpty() -> ErrorState.errorMessage(myRequiredAndDisabledPlugins.map { PluginId.getId(it) })
      else -> {
        val validationMessage = myLanguageSettings.validate(null, locationString)
        if (validationMessage != null) myAdvancedSettings.setOn(true)
        validationMessage
      }
    }
    if (validationMessage != null) {
      myErrorComponent.setErrorMessage(validationMessage)
      myErrorComponent.isVisible = true
      revalidate()
    }
    else {
      myErrorComponent.isVisible = false
    }
    myValidationListener?.onInputDataValidated(validationMessage == null)
  }

  private fun createLocationField(): LabeledComponent<TextFieldWithBrowseButton> {
    val field = TextFieldWithBrowseButton(myPathField)
    field.addBrowseFolderListener("Select Course Location", "Select course location", null,
                                  FileChooserDescriptorFactory.createSingleFolderDescriptor())
    return LabeledComponent.create(field, "Location:", BorderLayout.WEST)
  }

  private fun onCourseDataSelected(courseData: CourseData) {
    val courseName = "${courseData.displayName.capitalize().replace(File.separatorChar, '_')} Course"
    val file = FileUtil.findSequentNonexistentFile(File(ProjectUtil.getBaseDir()), courseName, "")
    if (!myTitleField.isChangedByUser) {
      myTitleField.setTextManually(file.name)
      if (!myPathField.isChangedByUser) {
        myPathField.setTextManually(file.absolutePath)
      }
    }

    val configurator = EduConfiguratorManager.findConfigurator(courseData.courseType, courseData.environment,
                                                               courseData.language) ?: return
    myCourse.language = courseData.language.id
    myCourse.environment = courseData.environment
    myLanguageSettings = configurator.courseBuilder.getLanguageSettings()
    myLanguageSettings.addSettingsChangeListener { doValidation() }

    val settings = arrayListOf<LabeledComponent<*>>(myLocationField)
    settings.addAll(myLanguageSettings.getLanguageSettingsComponents(myCourse, context))
    myAdvancedSettings.setSettingsComponents(settings)

    myRequiredAndDisabledPlugins = getDisabledPlugins(configurator.pluginRequirements)
    myDescriptionTextArea.text = myCourse.description.nullize() ?: """
      ${courseData.displayName} course.
      Created: ${LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.ENGLISH))}.
    """.trimIndent()
    doValidation()
  }

  private fun collectCoursesData(course: Course?): List<CourseData> {
    val courseData = if (course != null) {
      listOfNotNull(obtainCourseData(course.languageID, course.environment, course.itemType))
    }
    else {
      EduConfiguratorManager.allExtensions()
        .filter { it.instance.isCourseCreatorEnabled }
        .filter { it.courseType == myCourse.itemType }.mapNotNull { extension -> obtainCourseData(extension) }
    }
    return courseData.sortedBy { it.displayName }
  }

  private fun obtainCourseData(languageId: String, environment: String, courseType: String): CourseData? {
    val language = getLanguageById(languageId) ?: return null
    val extension = EduConfiguratorManager.findExtension(courseType, environment, language) ?: return null
    return obtainCourseData(extension, language)
  }

  private fun obtainCourseData(
    extension: EducationalExtensionPoint<EduConfigurator<*>>,
    language: Language? = getLanguageById(extension.language)
  ): CourseData? {
    if (language == null) return null
    val environment = extension.environment
    val courseType = extension.courseType
    val displayName = extension.displayName ?: run {
      when (courseType) {
        EduNames.PYCHARM -> if (environment == EduNames.DEFAULT_ENVIRONMENT) language.displayName else "${language.displayName} ($environment)"
        else -> "$courseType ${language.displayName}"
      }
    }

    return CourseData(language, courseType, environment, displayName, extension.instance.logo)
  }

  private fun getLanguageById(languageId: String): Language? {
    return Language.findLanguageByID(languageId) ?: run {
      LOG.info("Language with id $languageId not found")
      null
    }
  }

  companion object {
    private val LOG = Logger.getInstance(CCNewCoursePanel::class.java)
  }

  interface ValidationListener {
    fun onInputDataValidated(isInputDataComplete: Boolean)
  }

  data class CourseData(
    val language: Language,
    val courseType: String,
    val environment: String,
    val displayName: String,
    val icon: Icon?
  )

  private class CourseTitleDocument : PlainDocument() {
    override fun insertString(offs: Int, str: String?, a: AttributeSet?) {
      if (str == null || str.none { it in ILLEGAL_CHARS }) {
        super.insertString(offs, str, a)
      }
    }

    companion object {
      private val ILLEGAL_CHARS = arrayOf(File.separatorChar, '/', '|', ':')
    }
  }

  private class PathField : CCSyncTextField() {
    override fun doSync(complementaryTextField: CCSyncTextField) {
      val path = text ?: return
      val lastSeparatorIndex = path.lastIndexOf(File.separator)
      if (lastSeparatorIndex >= 0 && lastSeparatorIndex + 1 < path.length) {
        complementaryTextField.setTextManually(path.substring(lastSeparatorIndex + 1))
      }
    }
  }

  private class CourseTitleField : CCSyncTextField() {
    override fun doSync(complementaryTextField: CCSyncTextField) {
      val courseName = text ?: return
      val path = complementaryTextField.text?.trim() ?: return
      val lastSeparatorIndex = path.lastIndexOf(File.separator)
      if (lastSeparatorIndex >= 0) {
        complementaryTextField.setTextManually(path.substring(0, lastSeparatorIndex + 1) + courseName)
      }
    }
  }
}
