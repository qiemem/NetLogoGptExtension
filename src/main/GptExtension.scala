package org.nlogo.extensions.gpt


import org.nlogo.api.{
  Argument,
  Context,
  DefaultClassManager,
  ExtensionException,
  ExtensionManager,
  PrimitiveManager,
  Reporter,
  Command
}
import org.nlogo.core.Syntax
import requests._
import upickle.default.{ReadWriter => RW, macroRW, read, write}

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

  override def load(manager: PrimitiveManager): Unit = {
    manager.addPrimitive("process", GptProcessCommand)
    manager.addPrimitive("set-api-key", GptSetApiKeyCommand)
  }

  object GptProcessCommand extends Reporter {
    override def getSyntax: Syntax = Syntax.reporterSyntax(
      right = List(Syntax.StringType),
      ret = Syntax.StringType
    )
    override def report(args: Array[Argument], context: Context): AnyRef = {
      val inputText: String = args(0).getString
      val token: String =
        apiKey.getOrElse(throw new ExtensionException("API key not set."))
      val apiUrl = "https://api.openai.com/v1/chat/completions"
      val headers = Map(
        "Authorization" -> s"Bearer $token",
        "Content-Type" -> "application/json"
      )
      val requestBody = write(
        ChatRequest("gpt-3.5-turbo", Array(ChatMessage("user", inputText)))
      )
      val response = post(apiUrl, headers = headers, data = requestBody)
      response.statusCode match {
        case 200 =>
          read[ChatResponse](response.text()).choices(0).message.content
        case _ =>
          throw new ExtensionException(
            s"Failed to process text: ${response}"
          )
      }
    }
  }

  object GptSetApiKeyCommand extends Command {
    override def getSyntax: Syntax =
      Syntax.commandSyntax(right = List(Syntax.StringType))
    override def perform(args: Array[Argument], context: Context): Unit = {
      apiKey = Some(args(0).getString)
    }
  }
}
