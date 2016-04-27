package olx.down

import akka.actor.Actor
import olx.down.Downl.{FetchAdv, FetchAdvUrls, ParseResponse}
import olx._
import org.joda.time.DateTime
import org.json4s.JsonAST.JString
import org.json4s.jackson.JsonMethods._
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.openqa.selenium.By
import play.api.libs.ws._

import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}

/**
  * Created by stanikol on 21.04.16.
  */
object Downl {
  case class FetchAdvUrls(val url: String)
  case class FetchAdv( val url: String)
  type ParseResponse = (String, WSClient) => Data
}

class Downl extends Actor {

  var ws: WSClient = _
  var url: String = _

  implicit val mat = akka.stream.ActorMaterializer()

  override def preStart = ws = ahc.AhcWSClient()

  override def postStop = ws.close()

  def receive: Receive = {
    case command @ (_:FetchAdvUrls|_:FetchAdv) =>
      val sender_ = sender()
      println(s"${self.path.name} received command $command")
      val (link, fetchData) = command match {
        case FetchAdv(u)     =>
          url = u
          (u, fetchAdv(_, _))
        case FetchAdvUrls(u) =>
          url = u
          (u, fetchAdvUrls(_, _))
      }
      sender_ ! Try(fetchData(link, ws)).recoverWith({case error: Throwable => Success(Error(url, error))}).get
      println(s"${self.path.name} received finished $command")
  }


  private def fetchAdvUrls(url:String, wSClient: WSClient): Data = {
    val wSResponseF: Future[WSResponse] = ws.url(url).withRequestTimeout(Cfg.kill_actor_when_no_response).get()
    val wSResponse: WSResponse = Await.result(wSResponseF, Cfg.kill_actor_when_no_response)
    val soup = Jsoup.parse(wSResponse.body)
    val links: List[String] = soup.select("a.detailsLink").map { el: Element => el.attr("href").split("#")(0) } toList
    val nextPageUrl: Option[String] = Try(soup.select("a.pageNextPrev:contains(Следующее)").last().attr("href")).toOption
    val advUrls = new AdvUrls(links, nextPageUrl)
    advUrls
  }

  private def parseUserID(url:String): String =
  """http://.*ID(.*?)\.html.*""".r.findFirstMatchIn(url).get.group(1)

  private def fetchAdv(url:String, wSClient: WSClient): Data = {
    val wSResponseF: Future[WSResponse] = ws.url(url).withRequestTimeout(Cfg.kill_actor_when_no_response).get()
    val wSResponse: WSResponse = Await.result(wSResponseF, Cfg.kill_actor_when_no_response)
    val soup = Jsoup.parse(wSResponse.body)
//    val adv = Adv(
//      Map(
//        "siteid"   -> xpath("//span[contains(text(),'Номер объявления:')]/span[last()]"),
//        "brief"    -> xpath("//table[contains(., 'Объявление от')]"),
//        "text"     -> {css(".offerheadinner h1") + "\n" + css("#textContent")},
//        "added"    -> xpath("//span[contains(.,'Добавлено') or contains(.,'Опубликовано с мобильного')]"),
//        "url"      -> stripURL(url),
//        //        "user"     -> toe(xpath("(//a[@id='linkUserAds'])[1]", Some("href"))),
//        "user"     -> toe(xpath("(//a[contains(., 'Все другие объявления пользователя')])[1]", Some("href"))),
//        "phones"   -> toe(css(".contactitem")),
//        "price"    -> css("div.pricelabel strong"),
//        "head"     -> css(".offerheadinner h1"),
//        "location" -> css(".show-map-link"),
//        "userName" -> css(".userdetails span"),
//        "section"  -> toe(webDriver.findElements(By.cssSelector("#breadcrumbTop ul span")).map(_.getText).mkString),
//        "viewed"   -> xpath("//div[contains(., 'Просмотры:') and @class='pdingtop10']/strong"),
//        "scraped"  -> DateTime.now().toString("yyyy-MM-dd HHmmss")
//      )
//    )
    var items: Map[String,String] = Map(
        "siteid"   -> soup.select("span:contains(Номер объявления:) > span > span").last().text(),
        "brief"    -> soup.select("table:contains(Объявление от) > tbody > tr > td").map(_.text).mkString("\n"),
        "head"     -> soup.select(".offerheadinner h1").head.text(),
        "text"     -> soup.select("#textContent").head.text(),
        "added"    -> soup.select("span:matches((Добавлено|Опубликовано с мобильного))").head.text(),
        "url"      -> url,
        "user"     -> Try( soup.select("a:matches((?i)(Все другие объявления пользователя|Другие объявления автора))").head.attr("href") ).getOrElse(""),
        "price"    -> soup.select("div.pricelabel strong").head.text(),
        "location" -> soup.select(".show-map-link").head.text(),
        "userName" -> soup.select(".userdetails span").head.text(),
        "section"  -> soup.select("#breadcrumbTop ul span").map(_.text()).mkString,
        "viewed"   -> soup.select("div.pdingtop10:contains(Просмотры:) > strong").head.text(),
        "scraped"  -> DateTime.now().toString("yyyy-MM-dd HHmmss")

    )

    // TODO phones ! Часто два кототких телефона сливаются в один напр: 705-05-05 444-54-09 превращаются в 70505054445409. Tелефоны не разделены \n !!
      val id = parseUserID(url)
      val advF = ws.url(s"http://olx.ua/ajax/misc/contact/phone/$id/white/").get().map { wSResponse: WSResponse =>
//        println(wSResponse.body)
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
            }.filter(_.length > 7).mkString("\n")
        items = items.updated("phones", phones)
        Adv(items)
      }
    Await.result(advF, Cfg.kill_actor_when_no_response)
  }

}
