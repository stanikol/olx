package olx

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.{
  EntityStreamingSupport,
  JsonEntityStreamingSupport
}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import spray.json.DefaultJsonProtocol

import scala.io.StdIn

object Server
    extends App
    with SprayJsonSupport
    with Directives
    with DefaultJsonProtocol {

  final case class Order(url: String, max: Int)

  val jsonContentType = ContentType(
    MediaTypes.`application/json`.withParams(Map("charset" -> "utf-8"))
  )

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val orderFormat = jsonFormat2(Order)
  implicit val streamingSupport: EntityStreamingSupport =
    EntityStreamingSupport.json()
  implicit val jsonStreamingSupport: JsonEntityStreamingSupport =
    EntityStreamingSupport.json()
  val route =
    pathSingleSlash {
      get {
        val html = scala.io.Source
          .fromInputStream(getClass.getResourceAsStream("/html/index.html"))
          .mkString
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, html))
      }
    } ~
      path("download.json") {
        post {
          parameters('url.as[String], 'max.as[Int], 'format.?("json")) {
            (url, max, format) =>
              complete(olx.GrabOlx.createDownloadStream(url, max))
          } ~
            entity(as[Order]) { order =>
              complete(olx.GrabOlx.createDownloadStream(order.url, order.max))
            } ~
            formFields('url, 'max.as[Int], 'format.?("json")) {
              (url, max, format) =>
                complete(olx.GrabOlx.createDownloadStream(url, max))

            }
        }
      }
  val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

  println(
    s"Сервер запущен http://localhost:8080/\nНажмите RETURN чтобы прекратить работу..."
  )
  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())

}
