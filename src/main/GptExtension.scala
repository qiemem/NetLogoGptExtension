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
      val inputText = args(0).getString
      val token =
        apiKey.getOrElse(throw new ExtensionException("API key not set."))
      val apiUrl = "https://api.openai.com/v1/engines/davinci-codex/completions"
      val headers = Map(
        "Authorization" -> s"Bearer $token",
        "Content-Type" -> "application/json"
      )
      val requestBody = s"""{
                            |  "prompt": "$inputText",
                            |  "max_tokens": 16
                            |}""".stripMargin
      val response = post(apiUrl, headers = headers, data = requestBody)
      response.statusCode match {
        case 200 => response.text()
        case _ =>
          throw new ExtensionException(
            s"Failed to process text. Status code: ${response.statusCode}"
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
