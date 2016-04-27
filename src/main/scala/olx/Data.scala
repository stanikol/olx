package olx

import java.io.{File, FilenameFilter}
import java.text.SimpleDateFormat
import java.util.Locale

import org.joda.time.DateTime
import org.json4s.DefaultFormats
import org.json4s.JsonAST.JString
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization._
import org.openqa.selenium.{By, WebDriver}
import org.slf4j.LoggerFactory

import scala.collection.JavaConversions._
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

/**
  * Created by stanikol on 29.03.16.
  */
sealed trait Data {
  val log = LoggerFactory.getLogger(this.getClass)

  def stripURL(fullURL: String) = fullURL.split("#")(0)

  def css[WD<:WebDriver](css: String, attr: Option[String] = None)(implicit webDriver:WD): String =  {
    if (attr.isDefined)
      webDriver.findElement(By.cssSelector(css)).getAttribute(attr.get)
    else
      webDriver.findElement(By.cssSelector(css)).getText
  }


  def xpath[WD<:WebDriver](css: String, attr: Option[String] = None)(implicit webDriver:WD) =  {
    if (attr.isDefined)
      webDriver.findElement(By.xpath(css)).getAttribute(attr.get)
    else
      webDriver.findElement(By.xpath(css)).getText
  }

  def toe(s: => String, retrys: Int = 1): String =
    Try(s).recoverWith({
      case e: Throwable =>
        Thread.sleep(1000)
        if (retrys > 0) Try(toe(s, retrys-1))
        else {
          Success("")
        }
    }).get
}


case class AdvUrls(links: List[String], nextPage: Option[String]) extends Data

object AdvUrls extends Data{
  def apply(page: String)(implicit webDriver: WebDriver): AdvUrls = {
    webDriver.get(page)
    val adLinks: List[String] =
      webDriver.findElements(By.cssSelector("a.detailsLink")).map{el => stripURL(el.getAttribute("href"))}.toList
    val nextPage: Option[String] =
    Try {
        webDriver.findElements(By.cssSelector("a.pageNextPrev"))
            .filter(_.getText.contains("Следующее")).last.getAttribute("href")
      }.toOption
    webDriver.get("about:blank")
    new AdvUrls(adLinks, nextPage)
  }
}

case class Adv(items: Map[String, String]) extends Data {

  implicit val json4sDefaultFormats = DefaultFormats

  override def toString: String = writePretty(items)

  def url = stripURL(items("url"))

  def getOrElse(key: String, ifelse: String = "#error"): String = items.getOrElse(key, ifelse)

  def parseUserID = Adv.parseUserID(getOrElse("url"))

  def parseBriefs: Map[String, String] = {
    val briefs = items("brief").split("\n").filter(_.replace(" ", "").nonEmpty).toList
    val regexes = Cfg.brief_regexes.map(new Regex(_))
    assert(briefs.forall { b =>
      val found = regexes.find(r => b.matches(r.regex)).isDefined
      if(!found) log.error(s"Can't find regex to parse '$b'")
      found },
      s"Can't find regex to parse one of ${briefs.mkString(",")}"
    )
    val briefsAsMap: List[Map[String, String]] =
      for {brief <- briefs
           reg <- regexes if brief.matches(reg.regex)
      } yield brief match {
        case reg(k, v) => Map(k -> v)
        case x => Map.empty[String, String]
      }
    briefsAsMap.fold(Map.empty)((a, b) => a ++ b)
  }

  def parseTimeAdded: DateTime = Adv.parseTimeAdded(getOrElse("added"))
}

object Adv extends Data {

  implicit val jsonFormats = org.json4s.DefaultFormats

  def apply(url: String)(implicit webDriver: WebDriver): Adv = {
    webDriver.get(url)
//    try {
//      val contact = webDriver.findElement(By.className("contactitem"))
//      if (contact != null) contact.click()
////      if (contact != null) contact.click()
////      Thread.sleep(1000)
//    } catch {
//      case NonFatal(error) =>
//        log.error(s"Can't click `.contactitem` on $url\n${error.getMessage}")
//    }
    val adv = Adv(
      Map(
        "siteid"   -> xpath("//span[contains(text(),'Номер объявления:')]/span[last()]"),
        "brief"    -> xpath("//table[contains(., 'Объявление от')]"),
        "text"     -> {css(".offerheadinner h1") + "\n" + css("#textContent")},
        "added"    -> xpath("//span[contains(.,'Добавлено') or contains(.,'Опубликовано с мобильного')]"),
        "url"      -> stripURL(url),
//        "user"     -> toe(xpath("(//a[@id='linkUserAds'])[1]", Some("href"))),
        "user"     -> toe(xpath("(//a[contains(., 'Все другие объявления пользователя')])[1]", Some("href"))),
        "phones"   -> toe(css(".contactitem")),
        "price"    -> css("div.pricelabel strong"),
        "head"     -> css(".offerheadinner h1"),
        "location" -> css(".show-map-link"),
        "userName" -> css(".userdetails span"),
        "section"  -> toe(webDriver.findElements(By.cssSelector("#breadcrumbTop ul span")).map(_.getText).mkString),
        "viewed"   -> xpath("//div[contains(., 'Просмотры:') and @class='pdingtop10']/strong"),
        "scraped"  -> DateTime.now().toString("yyyy-MM-dd HHmmss")
      )
    )
    // TODO phones ! Часто два кототких телефона сливаются в один напр: 705-05-05 444-54-09 превращаются в 70505054445409. Tелефоны не разделены \n !!
    if(adv.items("phones").contains("x") || adv.items("phones").isEmpty){
      val id = parseUserID(url)
      webDriver.get(s"http://olx.ua/ajax/misc/contact/phone/$id/white/")
      val response: String = (parse(webDriver.findElement(By.xpath("//pre")).getText) \ "value") match {
        case JString(s) => s; case _ => "" }
      val phones = """([\d\(\)\-\s\+]+)""".r
        .findAllMatchIn(response)
          .map{ mo =>
            val phone = """[\(\)\-\s\+]+""".r.replaceAllIn(mo.group(1), "")
            if(phone.length > 12) { // if two phones has been concatenated as it said in TODO
              val splt = phone.splitAt(phone.length/2)
              splt._1 +"\n"+splt._2
            } else phone
          }.filter(_.length>7).mkString("\n")
      adv.copy(items = adv.items.updated("phones", phones))
    }
    else adv
  }

  def readStoredURLs(file: File = Cfg.savedir): Try[Set[String]] =
    for( advs <- readFromFile(file) )
      yield advs.foldLeft(Set.empty[String])((acc, adv) => acc + stripURL(adv.items("url")) )

  def readFromFile(file: File = Cfg.savedir): Try[Stream[Adv]] = Try {
    val result = if(file.isDirectory){
      val logFilesInDir: Array[File] =
        file.listFiles(new FilenameFilter {
          override def accept(dir: File, name: String): Boolean = name.endsWith(".log")})
      logFilesInDir.flatMap { f => readFromFile(f).get }.toStream
    } else {
      val txt = scala.io.Source.fromFile(file).getLines().mkString("\n").dropRight(1)
      parse("["+txt+"]").extract[List[Map[String, String]]].map{ items =>
        Adv(items.updated("url", stripURL(items.getOrElse("url",""))))
      }.toStream
    }
    println(s"${result.length} ads are read from ${file.getAbsolutePath}")
    result
  } recoverWith { case error: Throwable =>
    //    log.error(s"\n#ERROR Adv.apply(${file.getAbsoluteFile}):\n\t" + error.getMessage)
    Failure(error)
  }

def parseUserID(url:String): String =
  """http://.*ID(.*?).html""".r.findFirstMatchIn(stripURL(url)).get.group(1)

def parseTimeAdded(addedStr:String): DateTime = {
  val rusLocale = new Locale.Builder().setLanguage("ru").setScript("Cyrl").build();
  val timeAddedStr = "(?:Добавлено: в |Опубликовано с мобильного в )".r.replaceAllIn(addedStr.split(",").dropRight(1).mkString(","), "")
  val pattern = "HH:mm, dd MMMM yyyy"
  new DateTime(new SimpleDateFormat(pattern, rusLocale).parse(timeAddedStr))
}


}

case class Error(url: String, error: Throwable) extends Data
