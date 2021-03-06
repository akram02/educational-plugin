package com.jetbrains.edu.python.learning

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ComboboxWithBrowseButton
import com.intellij.util.PlatformUtils
import com.jetbrains.edu.python.learning.newproject.PyFakeSdkType
import com.jetbrains.edu.python.learning.newproject.PySdkSettingsHelper
import com.jetbrains.python.sdk.PyDetectedSdk
import com.jetbrains.python.sdk.PythonSdkType

class PyIdeaSdkSettingsHelper : PySdkSettingsHelper {

  override fun isAvailable(): Boolean = !(PlatformUtils.isPyCharm() || PlatformUtils.isCLion())

  override fun getInterpreterComboBox(fakeSdk: Sdk?, onSdkSelected: (Sdk?) -> Unit): ComboboxWithBrowseButton {
    val project = ProjectManager.getInstance().defaultProject
    val model = ProjectSdksModel()
    model.reset(project)
    if (fakeSdk != null) {
      model.addSdk(fakeSdk)
    }

    model.addListener(object : SdkModel.Listener {
      override fun sdkAdded(sdk: Sdk) = SdkConfigurationUtil.addSdk(sdk)
      override fun beforeSdkRemove(sdk: Sdk) {}
      override fun sdkChanged(sdk: Sdk, previousName: String) {}
      override fun sdkHomeSelected(sdk: Sdk, newSdkHome: String) {}
    })

    val sdkTypeIdFilter = Condition<SdkTypeId> { it == PythonSdkType.getInstance() || it == PyFakeSdkType }
    val sdkFilter = JdkComboBox.getSdkFilter(sdkTypeIdFilter)
    // BACKCOMPAT: 2019.3
    @Suppress("DEPRECATION")
    val comboBox = JdkComboBox(model, sdkTypeIdFilter, sdkFilter, sdkTypeIdFilter, true)
    comboBox.addActionListener { onSdkSelected(comboBox, onSdkSelected) }

    if (fakeSdk != null) {
      comboBox.selectedJdk = fakeSdk
    }
    else {
      onSdkSelected(comboBox, onSdkSelected)
    }

    val comboBoxWithBrowseButton = ComboboxWithBrowseButton(comboBox)
    val setupButton = comboBoxWithBrowseButton.button
    // BACKCOMPAT: 2019.3
    @Suppress("DEPRECATION")
    comboBox.setSetupButton(setupButton, null, model, comboBox.model.selectedItem as JdkComboBox.JdkComboBoxItem?, null, false)
    return comboBoxWithBrowseButton
  }

  private fun onSdkSelected(comboBox: JdkComboBox, onSdkSelected: (Sdk?) -> Unit) {
    var selectedSdk = comboBox.selectedJdk
    if (selectedSdk == null) {
      val selectedItem = comboBox.selectedItem
      if (selectedItem is JdkComboBox.SuggestedJdkItem) {
        selectedSdk = PyDetectedSdk(selectedItem.path)
      }
    }
    onSdkSelected(selectedSdk)
  }

  override fun updateSdkIfNeeded(project: Project, sdk: Sdk?): Sdk? {
    if (sdk !is PyDetectedSdk) {
      return sdk
    }
    val name = sdk.name
    val sdkHome = WriteAction.compute<VirtualFile, RuntimeException> { LocalFileSystem.getInstance().refreshAndFindFileByPath(name) }
    val newSdk = SdkConfigurationUtil.createAndAddSDK(sdkHome.path, PythonSdkType.getInstance())
    if (newSdk != null) {
      updateOrShowError(newSdk, project)
      SdkConfigurationUtil.addSdk(newSdk)
    }
    return newSdk
  }

  override fun getAllSdks(): List<Sdk> = ProjectJdkTable.getInstance().getSdksOfType(PythonSdkType.getInstance())
}
