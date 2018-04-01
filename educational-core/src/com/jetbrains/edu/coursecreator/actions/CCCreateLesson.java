package com.jetbrains.edu.coursecreator.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.jetbrains.edu.learning.EduConfigurator;
import com.jetbrains.edu.learning.EduConfiguratorManager;
import com.jetbrains.edu.learning.EduNames;
import com.jetbrains.edu.learning.EduUtils;
import com.jetbrains.edu.learning.courseFormat.*;
import icons.EducationalCoreIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CCCreateLesson extends CCCreateStudyItemActionBase<Lesson> {
  public static final String TITLE = "Create New " + EduNames.LESSON_TITLED;

  public CCCreateLesson() {
    super(EduNames.LESSON_TITLED, TITLE, EducationalCoreIcons.Lesson);
  }

  @Override
  protected void addItem(@NotNull Course course, @NotNull Lesson item) {
    final LessonContainer lessonContainer = item.getContainer();
    lessonContainer.addLesson(item);
  }

  @Override
  protected Function<VirtualFile, ? extends StudyItem> getStudyOrderable(@NotNull final StudyItem item) {
    return (Function<VirtualFile, StudyItem>)file -> {
      if (item instanceof Lesson) {
        final Section section = ((Lesson)item).getSection();
        if (section != null) {
          return section.getLesson(file.getName());
        }
        return ((Lesson)item).getCourse().getItem(file.getName());
      }
      return null;
    };
  }

  @Override
  @Nullable
  protected VirtualFile createItemDir(@NotNull final Project project, @NotNull final Lesson item,
                                      @NotNull final VirtualFile parentDirectory, @NotNull final Course course) {
    EduConfigurator configurator = EduConfiguratorManager.forLanguage(course.getLanguageById());
    if (configurator == null) {
      LOG.info("Failed to get configurator for " + course.getLanguageID());
      return null;
    }
    return configurator.getCourseBuilder().createLessonContent(project, item, parentDirectory);
  }

  @Override
  protected int getSiblingsSize(@NotNull Course course, @Nullable StudyItem parentItem) {
    if (parentItem instanceof Section) {
      return ((Section)parentItem).getLessons().size();
    }
    return course.getItems().size();
  }

  @Nullable
  @Override
  protected StudyItem getParentItem(@NotNull Course course, @NotNull VirtualFile directory) {
    final Lesson lesson = EduUtils.getLesson(directory, course);
    if (lesson == null) {
      return course.getSection(directory.getName());
    }
    return lesson.getSection();
  }

  @Nullable
  @Override
  protected StudyItem getThresholdItem(@NotNull final Course course, @NotNull final VirtualFile sourceDirectory) {
    return EduUtils.getLesson(sourceDirectory, course);
  }

  @Override
  protected boolean isAddedAsLast(@NotNull VirtualFile sourceDirectory,
                                  @NotNull Project project,
                                  @NotNull Course course) {
    final Section section = course.getSection(sourceDirectory.getName());
    return section != null || sourceDirectory.equals(project.getBaseDir());
  }

  @Override
  protected void sortSiblings(@NotNull Course course, @Nullable StudyItem parentItem) {
    if (parentItem instanceof LessonContainer) {
      ((LessonContainer)parentItem).sortChildren();
    }
  }

  @Override
  protected String getItemName() {
    return EduNames.LESSON;
  }

  @Override
  public Lesson createAndInitItem(@NotNull Course course, @Nullable StudyItem parentItem, String name, int index) {
    Lesson lesson = new Lesson();
    lesson.setName(name);
    lesson.setCourse(course);
    lesson.setIndex(index);
    if (parentItem instanceof Section) {
      lesson.setSection((Section)parentItem);
    }
    return lesson;
  }
}