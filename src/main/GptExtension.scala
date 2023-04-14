package org.nlogo.extensions.gpt


import org.nlogo.api.{
  Argument,
  Context,
  DefaultClassManager,
  ExtensionException,
  ExtensionManager,
  PrimitiveManager,
  Reporter,
  Command,
  Agent
}
import org.nlogo.core.{Syntax, LogoList}
import requests._
import upickle.default.{ReadWriter => RW, macroRW, read, write}
import scala.collection.mutable.{WeakHashMap, ArrayBuffer}

case class ChatMessage(role: String, content: String)
object ChatMessage { implicit val rw: RW[ChatMessage] = macroRW }

case class ChatRequest(model: String, messages: Seq[ChatMessage])
object ChatRequest { implicit val rw: RW[ChatRequest] = macroRW }

case class Choice(index: Int, message: ChatMessage, finish_reason: String)
object Choice { implicit val rw: RW[Choice] = macroRW }

case class ChatResponse(id: String, created: Int, choices: Array[Choice])
object ChatResponse { implicit val rw: RW[ChatResponse] = macroRW }

class GptExtension extends DefaultClassManager {
  private var apiKey: Option[String] = None
  private var model: String = "gpt-3.5-turbo"
  private val messageHistory: WeakHashMap[Agent, ArrayBuffer[ChatMessage]] =
    WeakHashMap()

  override def load(manager: PrimitiveManager): Unit = {
    manager.addPrimitive("chat", ChatCommand)
    manager.addPrimitive("choose", ChooseCommand)
    manager.addPrimitive("set-api-key", SetApiKeyCommand)
    manager.addPrimitive("set-model", SetModelCommand)
    manager.addPrimitive("history", HistoryReporter)
    manager.addPrimitive("set-history", SetHistoryCommand)
  }

  override def clearAll() {
    messageHistory.clear()
  }

  def sendChat(messages: Seq[ChatMessage]): ChatMessage = {
    val token: String =
      apiKey.getOrElse(throw new ExtensionException("API key not set."))
    val apiUrl = "https://api.openai.com/v1/chat/completions"
    val headers = Map(
      "Authorization" -> s"Bearer $token",
      "Content-Type" -> "application/json"
    )
    val requestBody = write(
      ChatRequest(model, messages)
    )
    val response = post(apiUrl, headers = headers, data = requestBody)
    response.statusCode match {
      case 200 =>
        read[ChatResponse](response.text()).choices(0).message
      case _ =>
        throw new ExtensionException(
          s"Failed to process text: ${response}"
        )
    }

  }

  object ChatCommand extends Reporter {
    override def getSyntax: Syntax = Syntax.reporterSyntax(
      right = List(Syntax.StringType),
      ret = Syntax.StringType
    )
    override def report(args: Array[Argument], context: Context): AnyRef = {
      val inputText: String = args(0).getString
      val chat = ChatMessage("user", inputText)
      val messages = messageHistory.getOrElseUpdate(
        context.getAgent,
        ArrayBuffer.empty[ChatMessage]
      )
      messages += chat

      val responseMessage = sendChat(messages)

      messages += responseMessage
      responseMessage.content
    }
  }

  object ChooseCommand extends Reporter {
    override def getSyntax: Syntax = Syntax.reporterSyntax(
      right = List(Syntax.StringType, Syntax.ListType),
      ret = Syntax.StringType
    )
    override def report(args: Array[Argument], context: Context): AnyRef = {
      val choices = args(1).getList
      val inputText: String = args(
        0
      ).getString
      val chat = ChatMessage("user", inputText)
      val messages = messageHistory.getOrElseUpdate(
        context.getAgent,
        ArrayBuffer.empty[ChatMessage]
      )
      messages +=
        ChatMessage(
          "system",
          s"Pretend that you may only respond with one of the following lines. Do not say anything that does not match one of the following lines exactly even if none of the choices are valid:\n${choices
              .mkString("\n")}"
        )
      messages += chat

      val responseMessage = sendChat(messages)

      messages += responseMessage
      messages += ChatMessage("system", "Return to normal.")
      responseMessage.content
    }
  }

  object SetApiKeyCommand extends Command {
    override def getSyntax: Syntax =
      Syntax.commandSyntax(right = List(Syntax.StringType))
    override def perform(args: Array[Argument], context: Context): Unit = {
      apiKey = Some(args(0).getString)
    }
  }

  object SetModelCommand extends Command {
    override def getSyntax: Syntax =
      Syntax.commandSyntax(right = List(Syntax.StringType))
    override def perform(args: Array[Argument], context: Context): Unit = {
      model = args(0).getString
    }
  }

  object HistoryReporter extends Reporter {
    override def getSyntax: Syntax =
      Syntax.reporterSyntax(ret = Syntax.ListType)
    override def report(args: Array[Argument], context: Context): LogoList = {
      LogoList.fromIterator(
        messageHistory
          .getOrElse(context.getAgent, Seq.empty[ChatMessage])
          .map { case ChatMessage(role, content) =>
            LogoList(role, content)
          }
          .iterator
      )
    }
  }

  object SetHistoryCommand extends Command {
    override def getSyntax = Syntax.commandSyntax(right = List(Syntax.ListType))
    override def perform(args: Array[Argument], context: Context): Unit = {
      messageHistory.put(
        context.getAgent,
        args(0).getList
          .map {
            case l: LogoList if l.size == 2 =>
              ChatMessage(l(0).toString, l(1).toString)
            case _ =>
              throw new ExtensionException(
                "Error: Expected list of role/content pairs"
              )
          }
          .to[ArrayBuffer]
      )
    }
  }
}
