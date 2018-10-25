package olx


import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.EntityStreamingSupport
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import spray.json.DefaultJsonProtocol

import scala.io.StdIn

object OlxGrabServer extends App with SprayJsonSupport with Directives with DefaultJsonProtocol{
    final case class Order(url:String, max: Int, format: String)

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher
    implicit val orderFormat = jsonFormat3(Order)
    implicit val streamingSupport: EntityStreamingSupport = EntityStreamingSupport.json()

    val jsonContentType = ContentType(MediaTypes.`application/json`.withParams(Map("charset" -> "utf-8")))

    val route =
      pathSingleSlash {
        get {
          val html = scala.io.Source.fromInputStream(getClass.getResourceAsStream("/html/index.html")).mkString
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, html))
        }
      } ~
      path("download.json"){
        post{
          parameters('url.as[String], 'max.as[Int], 'format.?("json")) { (url, max, format) =>
            complete(HttpEntity(jsonContentType,
              olx.GrabOlx.createDownloadStream(url, max, GrabOlx.ResultEncoding.fromString(format))))
          } ~
          entity(as[Order]){ order =>
            complete(HttpEntity(jsonContentType,
              olx.GrabOlx.createDownloadStream(order.url, order.max, GrabOlx.ResultEncoding.fromString(order.format))))
          } ~
          formFields('url, 'max.as[Int], 'format.?("json")){(url, max, format) =>
            complete(HttpEntity(jsonContentType,
                      olx.GrabOlx.createDownloadStream(url, max, GrabOlx.ResultEncoding.fromString(format))))

          }
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Сервер запущен http://localhost:8080/\nНажмите RETURN чтобы прекратить работу...")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())

}

