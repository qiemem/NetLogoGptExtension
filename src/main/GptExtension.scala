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
import upickle.default.{ReadWriter => RW, macroRW, read, write}
import scala.collection.mutable.{WeakHashMap, ArrayBuffer}
import com.knuddels.jtokkit.{Encodings}
import com.knuddels.jtokkit.api.{EncodingType, ModelType}
import scala.collection.JavaConverters._
import sttp.client3._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}
import com.vladsch.flexmark.Extension
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import _root_.org.nlogo.api.AnonymousReporter
import scala.concurrent.Awaitable

case class ChatMessage(role: String, content: String)
object ChatMessage { implicit val rw: RW[ChatMessage] = macroRW }

case class ChatRequest(
    model: String,
    messages: Seq[ChatMessage],
    logit_bias: Map[Int, Double] = Map.empty[Int, Double]
)
object ChatRequest { implicit val rw: RW[ChatRequest] = macroRW }

case class Choice(index: Int, message: ChatMessage, finish_reason: String)
object Choice { implicit val rw: RW[Choice] = macroRW }

case class ChatResponse(id: String, created: Int, choices: Array[Choice])
object ChatResponse { implicit val rw: RW[ChatResponse] = macroRW }

class GptExtension extends DefaultClassManager {
  val backend = HttpClientFutureBackend()

  private var apiKey: Option[String] = None
  private var model: String = "gpt-3.5-turbo"
  private val messageHistory: WeakHashMap[Agent, ArrayBuffer[ChatMessage]] =
    WeakHashMap()

  private val enc =
    Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE)

  override def load(manager: PrimitiveManager): Unit = {
    manager.addPrimitive("chat", ChatReporter)
    manager.addPrimitive("chat-async", ChatAsyncReporter)
    manager.addPrimitive("choose", ChooseCommand)
    manager.addPrimitive("set-api-key", SetApiKeyCommand)
    manager.addPrimitive("set-model", SetModelCommand)
    manager.addPrimitive("history", HistoryReporter)
    manager.addPrimitive("set-history", SetHistoryCommand)
  }

  override def clearAll() {
    messageHistory.clear()
  }

  def sendChat(
      messages: Seq[ChatMessage],
      logitBias: Map[Int, Double] = Map.empty[Int, Double]
  ): Future[ChatMessage] = {
    val token: String =
      apiKey.getOrElse(throw new ExtensionException("API key not set."))
    val apiUrl = uri"https://api.openai.com/v1/chat/completions"
    val headers = Map(
      "Authorization" -> s"Bearer $token",
      "Content-Type" -> "application/json"
    )
    val requestBody = write(
      ChatRequest(model, messages, logit_bias = logitBias)
    )
    println(requestBody)
    val request = basicRequest.headers(headers).body(requestBody).post(apiUrl)
    request.send(backend).transform {
      case Success(Response(Right(body: String), _, _, _, _, _)) =>
        val message = read[ChatResponse](body).choices(0).message
        println(message)
        Success(message)
      case Success(resp) =>
        Failure(new ExtensionException(s"Unexpected response: $resp"))
      case Failure(e) => Failure(new ExtensionException(new Exception(e)))
    }
  }

  object ChatReporter extends Reporter {
    override def getSyntax: Syntax = Syntax.reporterSyntax(
      right = List(Syntax.StringType),
      ret = Syntax.StringType
    )
    override def report(args: Array[Argument], context: Context): AnyRef = {
      val inputText: String = args(0).getString
      val agent = context.getAgent
      val chat = ChatMessage("user", inputText)
      val messages = messageHistory.getOrElseUpdate(
        agent,
        ArrayBuffer.empty[ChatMessage]
      )
      messages += chat
      val responseMessage = Await.result(sendChat(messages), 30.seconds)

      messages += responseMessage
      responseMessage.content
    }
  }

  case class AwaitableReporter[T <: AnyRef, R](future: Awaitable[R])(
      processOnJobThread: (R) => T
  ) extends AnonymousReporter {
    override def syntax: Syntax =
      Syntax.reporterSyntax(right = List(), ret = Syntax.WildcardType)

    override def report(c: Context, args: Array[AnyRef]): AnyRef =
      processOnJobThread(Await.result(future, 30.seconds))
  }

  object ChatAsyncReporter extends Reporter {
    override def getSyntax: Syntax = Syntax.reporterSyntax(
      right = List(Syntax.StringType),
      ret = Syntax.ReporterType
    )

    override def report(args: Array[Argument], context: Context): AnyRef = {
      val chat = ChatMessage("user", args(0).getString)
      val messages = messageHistory.getOrElseUpdate(
        context.getAgent,
        ArrayBuffer.empty[ChatMessage]
      )
      messages += chat

      AwaitableReporter(sendChat(messages)) { response =>
        messages += response
        response.content
      }
    }
  }

  object ChooseCommand extends Reporter {
    override def getSyntax: Syntax = Syntax.reporterSyntax(
      right = List(Syntax.StringType, Syntax.ListType),
      ret = Syntax.StringType
    )
    override def report(args: Array[Argument], context: Context): AnyRef = {
      val choices = args(1).getList
      val logitBias = (choices
        .flatMap { el: AnyRef => enc.encode(el.toString).asScala }
        .map { _.intValue -> 100.0 }
        :+ (100257 -> 100.0) // Add EOT encoding
      ).toMap

      val inputText: String = args(0).getString
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

      val responseMessage =
        Await.result(sendChat(messages, logitBias), 30.seconds)

      messages(messages.length - 2) = chat
      messages(messages.length - 1) = responseMessage
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
