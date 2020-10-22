package olx.scrap

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.jdk.CollectionConverters._
import scala.util.Try

object Parser {

  private val log = LoggerFactory.getLogger(this.getClass)

  def parseHrefs(url: String)(responseBody: String): Option[(String, List[String])] =
    Try {
      val doc: Document = Jsoup.parse(responseBody)
      val links: List[String] = doc
        .select(".detailsLink")
        .asScala
        .map(_.attr("href"))
        .map(
          _.split("#")(0)
        ) // NB: get rid of anchor in url to make url filtering possible
        .toSet
        .toList
      log.info(s"Found {} links in [{}].", links.length, url)
      val nextPage: String =
        Try(doc.select(".pageNextPrev").last().attr("href")).getOrElse("")
      if (links.nonEmpty) Some((nextPage, links)) else None
    }.recover {
      case e =>
        log.error("Error parsing links for [{}]: {}!", url, e.getMessage)
        None
    }.get

  def parseAd(responseBody: String, url: String): Map[String, String] = {
    val soup: Document = Jsoup.parse(responseBody)
    def parseHtml(keyName: String, default: String = "")(
      parse: Document => String
    ): (String, String) = {
      keyName -> Try(parse(soup)).recover {
        case e =>
          log.error(
            "Error parsing {} from [{}]: {}!",
            keyName,
            url,
            e.getMessage
          )
          default
      }.get
    }
    val usrid: String = parseUserID(url)
    val data: Map[String, String] = Map(
      "html" -> responseBody,
      parseHtml("siteid") { soup: Document =>
        """(?:Номер объявления:\s*)?(\d+)""".r
          .findFirstMatchIn(
            soup.select("li:matches(Номер объявления)").asScala.head.text()
          )
          .get
          .group(1)
      },
      parseHtml("head")(
        _.select(".offer-titlebox h1").asScala.head.text().trim
      ),
      parseHtml("text")(_.select("#textContent").asScala.head.text()),
      parseHtml("pubdate")(
        _.select(
          "li:matches((Добавлено.*|Опубликовано с мобильного.*))"
        ).asScala.head.text()
      ),
      "url" -> url,
      "usrid" -> usrid,
      parseHtml("user")(
        _.select(
          "a:matches((?i)(Все другие объявления пользователя|Другие объявления автора))"
        ).asScala.head.attr("href")
      ),
      parseHtml("price")(_.select("div.pricelabel strong").asScala.head.text()),
//      parseHtml("location")(_.select(".show-map-link").asScala.head.text()),
      parseHtml("username")(_.select("div.quickcontact__user-name").text()),
      parseHtml("section")(
        _.select("#breadcrumbTop ul span").asScala
          .map(_.text())
          .filter(_.nonEmpty)
          .mkString("/")
      ),
      parseHtml("viewed")(
        _.select(
          "span.offer-bottombar__counter:contains(Просмотры:) > strong"
        ).asScala.head
          .text()
      ),
      "downtime" -> LocalDateTime
        .now()
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HHmmss"))
    )
    val brief: Map[String, String] = Try {
      soup
        .select(".offer-details__item")
        .asScala
        .toList
        .map(
          doc =>
            doc
              .select(".offer-details__name")
              .text() -> doc.select(".offer-details__value").text()
        )
        .toMap[String, String]
    }.getOrElse(Map.empty[String, String])
    //        log.info(s"OK: reading content from `$uri`.")
    data ++ brief
  }

  private def parseUserID(url: String): String =
    Try(
      """https?://.*ID(.*?)\.html.*""".r.findFirstMatchIn(url).get.group(1)
    ).recover {
      case error =>
        log.error("Error parsing userId from {}: {}!", url, error.getMessage)
        ""
    }.get

  def getPhoneToken(body: String, url: String): String = {
    Try(
      """var phoneToken\W*?=\W*?'(.*)';""".r.findFirstMatchIn(body).get.group(1)
    ).recover {
      case error =>
        log.error(
          "Error parsing phoneToken from [{}]: {}!",
          url,
          error.getMessage
        )
        ""
    }.get
  }

  def parsePhones(responseBody: String): String =
    Try {
      val json: JsValue = responseBody.parseJson
      val phones: String = json.convertTo[Phones].value
      phones
    }.recover {
      case e =>
        val m: String = e.getMessage
        log.error("Error parsing phones: {}!", m)
        m
    }.get

  case class Phones(value: String)

  object Phones {
    implicit val valueReader: RootJsonFormat[Phones] = jsonFormat1(Phones.apply)
  }

}
