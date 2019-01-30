@file:JvmName("LocalEduCourseMixins")

package com.jetbrains.edu.coursecreator.actions.mixins

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.ObjectCodec
import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.ser.BeanSerializerFactory
import com.jetbrains.edu.learning.EduNames
import com.jetbrains.edu.learning.JSON_FORMAT_VERSION
import com.jetbrains.edu.learning.courseFormat.*
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.serialization.SerializationUtils
import com.jetbrains.edu.learning.serialization.SerializationUtils.Json.FRAMEWORK_TYPE
import com.jetbrains.edu.learning.serialization.SerializationUtils.Json.ITEM_TYPE
import com.jetbrains.edu.learning.serialization.doDeserializeTask
import com.jetbrains.edu.learning.stepik.StepikNames

private const val VERSION = "version"
private const val TITLE = "title"
private const val LANGUAGE = "language"
private const val SUMMARY = "summary"
private const val PROGRAMMING_LANGUAGE = "programming_language"
private const val ITEMS = "items"
private const val NAME = "name"
private const val TASK_LIST = "task_list"
private const val FILES = "files"
private const val TASK_TYPE = "task_type"
private const val DESCRIPTION_TEXT = "description_text"
private const val DESCRIPTION_FORMAT = "description_format"
private const val TEXT = "text"
private const val IS_VISIBLE = "is_visible"
private const val FEEDBACK_LINK = "feedback_link"
private const val PLACEHOLDERS = "placeholders"
private const val TYPE = "type"
private const val LINK = "link"
private const val LINK_TYPE = "link_type"
private const val OFFSET = "offset"
private const val LENGTH = "length"
private const val PLACEHOLDER_TEXT = "placeholder_text"
private const val POSSIBLE_ANSWER = "possible_answer"
private const val DEPENDENCY = "dependency"
private const val COURSE_TYPE = "course_type"

@Suppress("unused", "UNUSED_PARAMETER") // used for json serialization
@JsonPropertyOrder(VERSION, SUMMARY, TITLE, PROGRAMMING_LANGUAGE, LANGUAGE, COURSE_TYPE, ITEMS)
@JsonSerialize(using = CourseSerializer::class)
abstract class LocalEduCourseMixin {
  @JsonProperty(TITLE)
  private lateinit var name: String

  @JsonProperty(SUMMARY)
  private lateinit var description: String

  @JsonProperty(PROGRAMMING_LANGUAGE)
  private lateinit var myProgrammingLanguage: String

  @JsonProperty(LANGUAGE)
  private lateinit var myLanguageCode: String

  @JsonProperty(COURSE_TYPE)
  private lateinit var courseType: String

  @JsonProperty(ITEMS)
  private lateinit var items: List<StudyItem>
}

@Suppress("UNUSED_PARAMETER", "unused") // used for json serialization
@JsonSerialize(using = SectionSerializer::class)
abstract class LocalSectionMixin {
  @JsonProperty(TITLE)
  private lateinit var name: String

  @JsonProperty(ITEMS)
  private lateinit var items: List<StudyItem>
}

@Suppress("UNUSED_PARAMETER", "unused") // used for json serialization
@JsonSerialize(using = LessonSerializer::class)
abstract class LocalLessonMixin {
  @JsonProperty(TITLE)
  private lateinit var name: String

  @JsonProperty(TASK_LIST)
  private lateinit var taskList: List<Task>
}

@Suppress("UNUSED_PARAMETER", "unused") // used for json serialization
@JsonSerialize(using = TaskSerializer::class)
abstract class LocalTaskMixin {
  @JsonProperty(NAME)
  private lateinit var name: String

  @JsonProperty(FILES)
  private lateinit var myTaskFiles: MutableMap<String, TaskFile>

  @JsonProperty(DESCRIPTION_TEXT)
  private lateinit var descriptionText: String

  @JsonProperty(DESCRIPTION_FORMAT)
  private lateinit var descriptionFormat: DescriptionFormat

  @JsonProperty(FEEDBACK_LINK)
  private lateinit var myFeedbackLink: FeedbackLink
}

@Suppress("UNUSED_PARAMETER", "unused") // used for json serialization
@JsonPropertyOrder(NAME, PLACEHOLDERS, IS_VISIBLE, TEXT)
abstract class TaskFileMixin {
  @JsonProperty(NAME)
  private lateinit var myName: String

  @JsonProperty(PLACEHOLDERS)
  private lateinit var myAnswerPlaceholders: List<AnswerPlaceholder>

  @JsonProperty(IS_VISIBLE)
  var myVisible: Boolean = true

  @JsonProperty(TEXT)
  private lateinit var myText: String
}

@Suppress("UNUSED_PARAMETER", "unused") // used for json serialization
@JsonPropertyOrder(OFFSET, LENGTH, DEPENDENCY, POSSIBLE_ANSWER, PLACEHOLDER_TEXT)
abstract class AnswerPlaceholderMixin {
  @JsonProperty(OFFSET)
  private var myOffset: Int = -1

  @JsonProperty(LENGTH)
  private var myLength: Int = -1

  @JsonProperty(DEPENDENCY)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private lateinit var myPlaceholderDependency: AnswerPlaceholderDependency

  @JsonProperty(POSSIBLE_ANSWER)
  private lateinit var myPossibleAnswer: String

  @JsonProperty(PLACEHOLDER_TEXT)
  private lateinit var myPlaceholderText: String
}

@Suppress("UNUSED_PARAMETER", "unused") // used for json serialization
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class AnswerPlaceholderDependencyMixin {
  @JsonProperty("section")
  private lateinit var mySectionName: String

  @JsonProperty("lesson")
  private lateinit var myLessonName: String

  @JsonProperty("task")
  private lateinit var myTaskName: String

  @JsonProperty("file")
  private lateinit var myFileName: String

  @JsonProperty("placeholder")
  private var myPlaceholderIndex: Int = -1

  @JsonProperty("is_visible")
  private var myIsVisible = true
}

@Suppress("UNUSED_PARAMETER", "unused") // used for json serialization
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class FeedbackLinkMixin {
  @JsonProperty(LINK_TYPE)
  private lateinit var myType: FeedbackLink.LinkType

  @JsonProperty(LINK)
  private var myLink: String? = null
}

class TaskSerializer : JsonSerializer<Task>() {
  override fun serialize(task: Task, generator: JsonGenerator, provider: SerializerProvider) {
    generator.writeStartObject()
    val serializer = getJsonSerializer(provider, Task::class.java)
    serializer.unwrappingSerializer(null).serialize(task, generator, provider)
    generator.writeObjectField(TASK_TYPE, task.taskType)
    generator.writeEndObject()
  }
}

open class StudyItemSerializer(private val clazz: Class<out StudyItem>) : JsonSerializer<StudyItem>() {
  override fun serialize(item: StudyItem, generator: JsonGenerator, provider: SerializerProvider) {
    generator.writeStartObject()
    val serializer = getJsonSerializer(provider, clazz)
    serializer.unwrappingSerializer(null).serialize(item, generator, provider)
    generator.writeObjectField(TYPE, itemType(item))
    generator.writeEndObject()
  }

  private fun itemType(lesson: StudyItem): String {
    var itemType = EduNames.LESSON
    if (lesson is FrameworkLesson) {
      itemType = SerializationUtils.Json.FRAMEWORK_TYPE
    }
    else if (lesson is Section) {
      itemType = EduNames.SECTION
    }
    return itemType
  }
}

class LessonSerializer : StudyItemSerializer(Lesson::class.java)
class SectionSerializer : StudyItemSerializer(Section::class.java)

class CourseSerializer : JsonSerializer<EduCourse>() {
  override fun serialize(course: EduCourse, generator: JsonGenerator, provider: SerializerProvider) {
    generator.writeStartObject()
    val serializer = getJsonSerializer(provider, EduCourse::class.java)
    serializer.unwrappingSerializer(null).serialize(course, generator, provider)
    generator.writeObjectField(VERSION, JSON_FORMAT_VERSION)
    generator.writeEndObject()
  }
}

private fun getJsonSerializer(provider: SerializerProvider, itemClass: Class<out StudyItem>): JsonSerializer<Any> {
  val javaType = provider.constructType(itemClass)
  val beanDesc: BeanDescription = provider.config.introspect(javaType)
  return BeanSerializerFactory.instance.findBeanSerializer(provider, javaType, beanDesc)
}

class TaskDeserializer @JvmOverloads constructor(vc: Class<*>? = null) : StdDeserializer<Task>(vc) {
  override fun deserialize(jp: JsonParser, ctxt: DeserializationContext?): Task? {
    val node: ObjectNode = jp.codec.readTree(jp) as ObjectNode
    return doDeserializeTask(node, jp.codec)
  }
}

class StudyItemDeserializer @JvmOverloads constructor(vc: Class<*>? = null) : StdDeserializer<StudyItem>(vc) {
  override fun deserialize(jp: JsonParser, ctxt: DeserializationContext?): StudyItem? {
    val node: ObjectNode = jp.codec.readTree(jp) as ObjectNode
    val item = deserializeItem(node, jp.codec)
    val name = item.name
    if (StepikNames.PYCHARM_ADDITIONAL == name) {
      item.name = EduNames.ADDITIONAL_MATERIALS
    }
    return item
  }

  private fun deserializeItem(jsonObject: ObjectNode, codec: ObjectCodec): StudyItem {
    return if (!jsonObject.has(ITEM_TYPE)) {
      codec.treeToValue(jsonObject, Lesson::class.java)
    }
    else {
      val itemType = jsonObject.get(ITEM_TYPE).asText()
      when (itemType) {
        EduNames.LESSON -> codec.treeToValue(jsonObject, Lesson::class.java)
        FRAMEWORK_TYPE -> codec.treeToValue(jsonObject, FrameworkLesson::class.java)
        EduNames.SECTION -> codec.treeToValue(jsonObject, Section::class.java)
        else -> throw IllegalArgumentException("Unsupported lesson type: $itemType")
      }
    }
  }
}