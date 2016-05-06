package olx

import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import play.api.libs.ws.{WSResponse, ahc}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.collection.JavaConversions._

/**
  * Created by stanikol on 06.05.16.
  */
object Scrap {
  private def parseUserID(url:String): String = """http://.*ID(.*?)\.html.*""".r.findFirstMatchIn(url).get.group(1)

  def fetchAdv(url:String)(implicit wsClient: ahc.AhcWSClient, ec: ExecutionContext): Future[Data] = {
    try {
      for { response: WSResponse <- wsClient.url(url).withRequestTimeout(Cfg.kill_actor_when_no_response).get
            soup: Document = Jsoup.parse(response.body)
            id = parseUserID(url)
            siteid = """(?:Номер объявления\:\s*)(\d+)""".r.findFirstMatchIn(soup.select("span:matches((Добавлено|Опубликовано с мобильного))").head.text()).get.group(1)
            phone <- getPhones(id)
            skype <- getSkype(id)
      } yield {
        val items = Map(
          //        "siteid"   -> soup.select("span:contains(Номер объявления:) > span > span").last().text(),
          "siteid" -> siteid,
          "brief" -> soup.select("table:contains(Объявление от) > tbody > tr > td").map(_.text).mkString("\n"),
          "head" -> soup.select(".offerheadinner h1").head.text(),
          "text" -> soup.select("#textContent").head.text(),
          "added" -> soup.select("span:matches((Добавлено|Опубликовано с мобильного))").head.text(),
          "url" -> url,
          "user" -> Try(soup.select("a:matches((?i)(Все другие объявления пользователя|Другие объявления автора))").head.attr("href")).getOrElse(""),
          "price" -> soup.select("div.pricelabel strong").head.text(),
          "location" -> soup.select(".show-map-link").head.text(),
          "userName" -> soup.select(".userdetails span").head.text(),
          "section" -> soup.select("#breadcrumbTop ul span").map(_.text()).mkString,
          "viewed" -> soup.select("div.pdingtop10:contains(Просмотры:) > strong").head.text(),
          "scraped" -> DateTime.now().toString("yyyy-MM-dd HHmmss"),
          "phones" -> phone
        )
        Adv(if (!skype.isEmpty) items.updated("skype", skype) else items)
      }
    }
    catch {
      case e: Throwable => Future.successful(Error(url, e.getMessage))
    }
  }

  def fetchAdvUrls(url:String)(implicit wsClient: ahc.AhcWSClient, ec: ExecutionContext): Future[Data] = {
    try {
      for( response <- wsClient.url(url).withRequestTimeout(Cfg.kill_actor_when_no_response).get )
        yield {
          val soup: Document = Jsoup.parse(response.body)
          val links: List[String] = soup.select("a.detailsLink").map { el: Element => el.attr("href").split("#")(0) }.toList
          val nextPageUrl: Option[String] = Try(soup.select("a.pageNextPrev:contains(Следующее)").last().attr("href")).toOption
          new AdvUrls(links, url, nextPageUrl)
        }
    } catch {
      case e: Throwable  => Future.successful(Error(url, e.getMessage))
    }
  }

  private def getPhones(id:String)(implicit wsClient: ahc.AhcWSClient, ec: ExecutionContext): Future[String] = {
    wsClient.url(s"http://olx.ua/ajax/misc/contact/phone/$id/white/").get().map { wSResponse: WSResponse =>
      val phones =
        """([\d\(\)\-\s\+]+)""".r
          .findAllMatchIn(wSResponse.body)
          .map { mo =>
            val phone = """[\(\)\-\s\+]+""".r.replaceAllIn(mo.group(1), "")
            if (phone.length > 12) {
              // if two phones has been concatenated as it said in TODO
              val splt = phone.splitAt(phone.length / 2)
              splt._1 + "\n" + splt._2
            } else phone
          }.filter(_.length >= 7).mkString("\n")
      if(wSResponse.body != "Извините, страница не найдена")
        phones
      else
        ""
      phones
    }
  }

  private def getSkype(id:String)(implicit wsClient: ahc.AhcWSClient, ec: ExecutionContext): Future[String] = {
    wsClient.url(s"http://olx.ua/ajax/misc/contact/skype/$id/").get().map { wSResponse: WSResponse =>
      if(wSResponse.body == "Извините, страница не найдена")
        ""
      else{
        """\{\"value\":\"(.*)\"\}""".r.findFirstMatchIn(wSResponse.body).map(mo => mo.group(1)).head
      }
    }
  }

}
