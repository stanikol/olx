package olx

import java.util.concurrent.atomic.AtomicLong

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Cookie, HttpCookie, HttpCookiePair}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import org.joda.time.DateTime
import org.jsoup.Jsoup
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
object GrabOlx {

  def createDownloadStream(startLink: String, maxDownloadCount: Int)(
    implicit as: ActorSystem,
    mat: ActorMaterializer,
    ec: ExecutionContext
  ): Source[JsValue, _] = {
    println(s"startLink=$startLink maxDownloadCount=$maxDownloadCount")
    val advertisements: Source[JsValue, _] = Source
      .unfoldAsync(startLink)(downloadAdvertisementsHref)
      .mapConcat(identity)
//      .via(Flow[String].deduplicate())
      .take(maxDownloadCount)
      .via(downloadAdvertisements)
      .via(downloadPhones)
//      .via(Flow[Map[String, String]].map(encoding.encode))
      .via(Flow[Map[String, String]].map(_.toJson))
    //
//    Source.combine(
//      Source.single(encoding.header),
//      advertisements,
//      Source.single(encoding.footer)
//    )(Concat(_))
    advertisements
  }

  def downloadAdvertisementsHref(uri: String)(
    implicit mat: ActorMaterializer,
    actorSystem: ActorSystem,
    ec: ExecutionContext
  ): Future[Option[(String, List[String])]] = {
    if (uri.isEmpty) Future.successful(None)
    else
      for {
        response <- Http().singleRequest(HttpRequest(uri = uri))
//        _= Thread.sleep(1000)
        html <- Unmarshal(response.entity).to[String]
        doc = Jsoup.parse(html)
        links = doc.select(".detailsLink").map { _.attr("href") }.toList
        _ = println(s"Found ${links.length} links. Url `$uri`.")
        nextPage = Try(doc.select(".pageNextPrev").last().attr("href"))
          .getOrElse("")
        res = if (links.nonEmpty) Some((nextPage, links)) else None
      } yield res
  }

  def downloadAdvertisements(
    implicit as: ActorSystem,
    mat: ActorMaterializer,
    ec: ExecutionContext
  ): Flow[String, (Map[String, String], Cookie, String, String), NotUsed] = {
    val counter = new AtomicLong(0)
    Flow[String].mapAsync(Config.numberOfDownloadThreads) { uri =>
      for {
        response <- Http().singleRequest(HttpRequest(uri = uri))
        _ = println(s"Opening  $uri ${counter.incrementAndGet()} ...")
        responseBody <- Unmarshal(response.entity).to[String]
        soup = Jsoup.parse(responseBody)
        usrid = parseUserID(uri)
        siteid = Try(
          """(?:Номер объявления:\s*)(\d+)""".r
            .findFirstMatchIn(
              soup
                .select("span:matches((Добавлено|Опубликовано с мобильного))")
                .head
                .text()
            )
            .get
            .group(1)
        ).toOption.getOrElse("")
        //
        phoneToken = getPhoneToken(responseBody)
        cookies = getCookies(response).map {
          case HttpCookie(name, _, _, _, _, _, _, _, _) if name.equals("pt") =>
            HttpCookiePair(name, phoneToken)
          case HttpCookie(name, value, _, _, _, _, _, _, _) =>
            HttpCookiePair(name, value)
        }
        //
        data = Map(
          "seq" -> counter.get().toString,
          "siteid" -> siteid,
          "brief" -> Try(
            soup
              .select("table:contains(Объявление от) > tbody > tr > td")
              .map(_.text)
              .mkString("\n")
          ).toOption.getOrElse(""),
          "head" -> soup
            .select(".offer-titlebox h1")
            .headOption
            .map(_.text().trim)
            .getOrElse(""),
          "text" -> soup
            .select("#textContent")
            .headOption
            .map(_.text())
            .getOrElse(""),
          "pubdate" -> soup
            .select("span:matches((Добавлено|Опубликовано с мобильного))")
            .headOption
            .map(_.text())
            .getOrElse(""),
          "url" -> uri,
          "usrid" -> Try(usrid).toOption.getOrElse(""),
          "user" -> Try(
            soup
              .select(
                "a:matches((?i)(Все другие объявления пользователя|Другие объявления автора))"
              )
              .head
              .attr("href")
          ).getOrElse(""),
          "price" -> soup
            .select("div.pricelabel strong")
            .headOption
            .map(_.text())
            .getOrElse(""),
          "location" -> soup
            .select(".show-map-link")
            .headOption
            .map(_.text())
            .getOrElse(""),
          "username" -> soup
            .select(".userdetails span")
            .headOption
            .map(_.text())
            .getOrElse(""),
          "section" -> Try(
            soup
              .select("#breadcrumbTop ul span")
              .map(_.text())
              .filter(_.nonEmpty)
              .mkString("/")
          ).toOption.getOrElse(""),
          "viewed" -> Try(
            soup
              .select("div.pdingtop10:contains(Просмотры:) > strong")
              .head
              .text()
          ).toOption.getOrElse(""),
          "downdate" -> DateTime.now().toString("yyyy-MM-dd HHmmss")
        )
        _ = println(s"OK: reading content from `$uri`.")
      } yield (data, Cookie(cookies), phoneToken, usrid)
    }
  }

  private def getPhoneToken(body: String) = {
    Try(
      """var phoneToken\W*?=\W*?'(.*)';""".r.findFirstMatchIn(body).get.group(1)
    ).toOption.getOrElse("")
  }

  private def getCookies(httpResponse: HttpResponse) =
    httpResponse.headers.collect {
      case c: akka.http.scaladsl.model.headers.`Set-Cookie` => c.cookie
    }

  private def parseUserID(url: String): String =
    Try("""https?://.*ID(.*?)\.html.*""".r.findFirstMatchIn(url).get.group(1)).toOption
      .getOrElse("")

  def downloadPhones(
    implicit as: ActorSystem,
    mat: ActorMaterializer,
    ec: ExecutionContext
  ): Flow[(Map[String, String], Cookie, String, String),
          Map[String, String],
          NotUsed] =
    Flow[(Map[String, String], Cookie, String, String)]
      .mapAsync(Config.numberOfDownloadThreads) {
        case (data, cookie, phoneToken, usrid) =>
          val phonesUri =
            s"https://www.olx.ua/ajax/misc/contact/phone/$usrid/?pt=$phoneToken"
          val phoneReq = HttpRequest(uri = phonesUri).withHeaders(cookie)
          println(
            s"Downloading phones for `${data("url")}` from `$phonesUri` ..."
          )
          for {
            response <- Http().singleRequest(phoneReq)
            body <- Unmarshal(response.entity).to[String]
            phones = parsePhones(body)
            updatedData = if (phones.nonEmpty) {
              println(s"OK: Downloaded phones for `${data("url")}`: $phones")
              data.updated("phones", phones)
            } else {
              println(
                s"WARN: No phones are downloaded for `${data("url")}`: $phones"
              )
              data
            }
          } yield updatedData

      }

  private def parsePhones(responseBody: String) = {
    val extractPhonesReg = """([\d\+\(][\d\s\-\(\)]+\d)""".r
    extractPhonesReg.findAllIn(responseBody).matchData.mkString(", ")
  }

  sealed trait ResultEncoding {
    val header: ByteString
    val footer: ByteString
    def encode(d: Map[String, String]): ByteString
  }
}
