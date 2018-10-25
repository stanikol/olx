package ria

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.model.headers.{Cookie, HttpCookiePair}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

import scala.collection.JavaConversions._

object GrabRia extends App {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val startUrl = """http://flow.gassco.no/"""
  val url2 = """http://flow.gassco.no/acceptDisclaimer?"""

  val req = HttpRequest(uri = startUrl)
  val result = for{
    response <-  Http().singleRequest(req)
    html <- Unmarshal(response).to[String]
    doc = Jsoup.parse(html)
    cookies = response.headers.collect {
      case c: akka.http.scaladsl.model.headers.`Set-Cookie` =>
        HttpCookiePair(c.cookie.name, c.cookie.value)
    }
    action = doc.select("form").map{e: Element =>
      e.attr("action")
    }.head
//    _=println(s"""response1.status = ${response.status}
//                 | headers1 = ${response.headers}
//                 | cookies1 = ${cookies}
//                 | html2 = $html""".stripMargin)
    /////////
    req2 = HttpRequest(uri = url2 + action).withHeaders(Cookie(cookies))
    response2 <-  Http().singleRequest(req2)
    html2 <- Unmarshal(response2.entity).to[String]
    cookies2 = response2.headers.collect {
      case c: akka.http.scaladsl.model.headers.`Set-Cookie` =>
        HttpCookiePair(c.cookie.name, c.cookie.value)
    }
//    _=println(s"""response2.status = ${response2.status}
//         | headers2 = ${response2.headers}
//         | cookies2 = ${cookies2}
//         | html2 = $html2""".stripMargin)
    ////////////
    response3 <-  Http().singleRequest(HttpRequest(uri = startUrl).withHeaders(Cookie(cookies)))
    html3 <- Unmarshal(response3).to[String]
//    _=println(s"""response3.status = ${response3.status}
//                 | headers3 = ${response3.headers}
//                 | html3 = $html3""".stripMargin)
    doc3 = Jsoup.parse(html3)
    txt = doc3.select("table.realTime td.flow").map{e: Element => e.text}.mkString(", ") +"\n"
  } yield {
    println("\nFLOW INFORMATION REAL-TIME =>\n" + txt)
  }

  result.onComplete(_=> system.terminate())
}
