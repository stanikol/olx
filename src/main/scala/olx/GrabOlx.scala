package olx

import java.util.concurrent.atomic.AtomicLong

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Cookie, HttpCookie, HttpCookiePair}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Concat, Flow, Source}
import akka.util.ByteString
import com.janschulte.akvokolekta.StreamAdditions._
import org.joda.time.DateTime
import org.jsoup.Jsoup
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object GrabOlx {

  def downloadAdvertisementsHref(uri: String)(implicit mat: ActorMaterializer, actorSystem: ActorSystem, ec: ExecutionContext): Future[Option[(String, List[String])]] = {
    if(uri.isEmpty) Future.successful(None)
    else for {
        response <- Http().singleRequest(HttpRequest(uri = uri))
//        _= Thread.sleep(1000)
        html <- Unmarshal(response.entity).to[String]
        doc = Jsoup.parse(html)
        links = doc.select(".detailsLink").map{_.attr("href")}.toList
        _ = println(s"Found ${links.length} links. Url `$uri`.")
        nextPage = Try(doc.select(".pageNextPrev").last().attr("href")).getOrElse("")
        res = if (links.nonEmpty) Some((nextPage, links)) else None
      } yield res
  }

  private def getPhoneToken(body: String) = {
    Try("""var phoneToken\W*?=\W*?'(.*)';""".r.findFirstMatchIn(body).get.group(1)).toOption.getOrElse("")
  }

  private def getCookies(httpResponse: HttpResponse) = httpResponse.headers.collect {
      case c: akka.http.scaladsl.model.headers.`Set-Cookie` => c.cookie
  }

  private def parseUserID(url:String): String =
    Try("""https?://.*ID(.*?)\.html.*""".r.findFirstMatchIn(url).get.group(1)).toOption.getOrElse("")

  private def parsePhones(responseBody: String) = {
    val extractPhonesReg = """([\d\+\(][\d\s\-\(\)]+\d)""".r
    extractPhonesReg.findAllIn(responseBody).matchData.mkString(", ")
  }

  def downloadAdvertisements(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext) = {
    val counter = new AtomicLong(0)
    Flow[String].mapAsync(Config.numberOfDownloadThreads){ uri =>
      for {
        response <- Http().singleRequest(HttpRequest(uri = uri))
        _ = println(s"Opening advertisement at $uri ${counter.incrementAndGet()}")
        responseBody <- Unmarshal(response.entity).to[String]
        soup = Jsoup.parse(responseBody)
        usrid = parseUserID(uri)
        siteid = Try("""(?:Номер объявления\:\s*)(\d+)""".r.findFirstMatchIn(soup.select("span:matches((Добавлено|Опубликовано с мобильного))").head.text()).get.group(1)).toOption.getOrElse("")
        //
        phoneToken = getPhoneToken(responseBody)
        cookies = getCookies(response).map {
          case HttpCookie(name, value, _, _, _, _, _, _, _) if name.equals("pt") =>
            HttpCookiePair(name, phoneToken)
          case x@HttpCookie(name, value, _, _, _, _, _, _, _) =>
            HttpCookiePair(name, value)
        }
        //
        data = Map(
          "seq" -> counter.get().toString,
          "siteid" -> siteid,
          "brief" -> Try(soup.select("table:contains(Объявление от) > tbody > tr > td").map(_.text).mkString("\n")).toOption.getOrElse(""),
          "head" -> soup.select(".offer-titlebox h1").headOption.map(_.text().trim).getOrElse(""),
          "text" -> soup.select("#textContent").headOption.map(_.text()).getOrElse(""),
          "pubdate" -> soup.select("span:matches((Добавлено|Опубликовано с мобильного))").headOption.map(_.text()).getOrElse(""),
          "url" -> uri,
          "usrid" -> Try(usrid).toOption.getOrElse(""),
          "user" -> Try(soup.select("a:matches((?i)(Все другие объявления пользователя|Другие объявления автора))").head.attr("href")).getOrElse(""),
          "price" -> soup.select("div.pricelabel strong").headOption.map(_.text()).getOrElse(""),
          "location" -> soup.select(".show-map-link").headOption.map(_.text()).getOrElse(""),
          "username" -> soup.select(".userdetails span").headOption.map(_.text()).getOrElse(""),
          "section" -> Try(soup.select("#breadcrumbTop ul span").map(_.text()).filter(_.nonEmpty).mkString("/")).toOption.getOrElse(""),
          "viewed" -> Try(soup.select("div.pdingtop10:contains(Просмотры:) > strong").head.text()).toOption.getOrElse(""),
          "downdate" -> DateTime.now().toString("yyyy-MM-dd HHmmss")
        )
      } yield (data, Cookie(cookies), phoneToken, usrid)
    }
  }

  def downloadPhones(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext) =
    Flow[(Map[String, String], Cookie, String, String)].mapAsync(Config.numberOfDownloadThreads){ case (data, cookie, phoneToken, usrid) =>
      val phonesUri = s"https://www.olx.ua/ajax/misc/contact/phone/$usrid/?pt=${phoneToken}"
      val phoneReq = HttpRequest(uri = phonesUri).withHeaders(cookie)
      for{
        response <- Http().singleRequest(phoneReq)
        body <- Unmarshal(response.entity).to[String]
        phones = parsePhones(body)
        updatedData = if(phones.nonEmpty) data.updated("phones",phones) else data
      } yield updatedData

  }

  sealed trait ResultEncoding{
    val header: ByteString
    val footer: ByteString
    def encode(d: Map[String, String]): ByteString
  }
  object ResultEncoding {
    val fromString: PartialFunction[String, ResultEncoding] = {
      case "pretty-json" => PrettyJSON
      case "json" => JSON
      case _ => JSON
    }
  }
  case object JSON extends ResultEncoding{
    val header = ByteString("[")
    val footer = ByteString("]")
    def encode(d: Map[String, String]): ByteString = ByteString(d.toJson.compactPrint+",\n")
  }
  case object PrettyJSON extends ResultEncoding {
    val header = ByteString("[\n")
    val footer = ByteString("]")
    def encode(d: Map[String, String]) = ByteString(d.toJson.prettyPrint+",\n")
  }

  def createDownloadStream(startLink: String, maxDownloadCount: Int, encoding: ResultEncoding=PrettyJSON)(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext)
    : Source[ByteString, NotUsed] ={
    println(s"startLink=$startLink maxDownloadCount=$maxDownloadCount")
    val advertisements: Source[ByteString, _] = Source
      .unfoldAsync(startLink)(downloadAdvertisementsHref)
      .mapConcat(identity)
      .via(Flow[String].deduplicate())
      .take(maxDownloadCount)
      .via(downloadAdvertisements)
      .via(downloadPhones)
      .via(Flow[Map[String, String]].map(encoding.encode))
    //
    Source.combine(
      Source.single(encoding.header),
      advertisements,
      Source.single(encoding.footer)
    )(Concat(_))
  }
}
