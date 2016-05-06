package olx

import java.io.{File, FilenameFilter}
import java.text.SimpleDateFormat
import java.util.Locale

import org.joda.time.DateTime
import org.json4s.DefaultFormats
import org.json4s.JsonAST.JString
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization._
//import org.openqa.selenium.{By, WebDriver}
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
}

case class AdvUrls(links: List[String], currentPage: String, nextPage: Option[String]) extends Data

case class Adv(items: Map[String, String]) extends Data {

  implicit val json4sDefaultFormats = DefaultFormats

  override def toString: String = writePretty(items)

  def url = stripURL(items("url"))

  def getOrElse(key: String, ifelse: String = "#error"): String = items.getOrElse(key, ifelse)

  def userID = Adv.parseUserID(getOrElse("url"))

  def briefs: Map[String, String] = {
    val briefsTxt: Seq[String] = items("brief").split("\n").filter(_.replace(" ", "").nonEmpty)
    val regexes: Seq[Regex] = Cfg.brief_regexes.map(new Regex(_))
    assert(briefsTxt.forall { b =>
      val found = regexes.find(r => b.matches(r.regex)).isDefined
      if(!found) log.error(s"Can't find regex to parse '$b'")
      found },
      s"Can't find regex to parse one of ${briefsTxt.mkString(",")}"
    )
    val briefsAsSeqOfMap: Seq[Map[String, String]] =
      for { brief <- briefsTxt
            reg   <- regexes if brief.matches(reg.regex)
      } yield brief match {
          case reg(k, v) => Map(k -> v)
          case x         => Map.empty[String, String]
      }
    briefsAsSeqOfMap.fold(Map.empty)((a, b) => a ++ b)
  }

  def timeAdded: DateTime = Adv.parseTimeAdded(getOrElse("added"))
}


object Adv extends Data {

  implicit val jsonFormats = org.json4s.DefaultFormats

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
      println(s"Reading ${file.getName}")
      val txt = scala.io.Source.fromFile(file).getLines().mkString("\n").dropRight(1)
      val filename = file.getName
      parse("["+txt+"]").extract[List[Map[String, String]]].map{ items =>
        val itemsWithUrl = items.updated("url", stripURL(items.getOrElse("url","")))
        Adv(
          if(items.keys.contains("filename"))  itemsWithUrl
          else itemsWithUrl.updated("filename", filename)
        )
      }.toStream
    }
    println(s"${result.length} ads are read from ${file.getAbsolutePath}")
    result
  } recoverWith { case error: Throwable =>
    Failure(error)
  }

  def readDB = readFromFile().get

  def parseUserID(url:String): String =
    """http://.*ID(.*?).html""".r.findFirstMatchIn(stripURL(url)).get.group(1)

  def parseTimeAdded(addedStr:String): DateTime = {
    val rusLocale = new Locale.Builder().setLanguage("ru").setScript("Cyrl").build();
    val timeAddedStr = "(?:Добавлено: в |Опубликовано с мобильного в )".r.replaceAllIn(addedStr.split(",").dropRight(1).mkString(","), "")
    val pattern = "HH:mm, dd MMMM yyyy"
    new DateTime(new SimpleDateFormat(pattern, rusLocale).parse(timeAddedStr))
  }


}

case class Error(url: String, error: String) extends Data
