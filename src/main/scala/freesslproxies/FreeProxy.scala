package freesslproxies:

  import cats.effect.IO
  import cats.instances.list.*
  import cats.syntax.all.*
  import org.apache.commons.csv.CSVRecord
  import org.olx.parser.StrToCssSelector
  import org.openqa.selenium.WebElement
  import wvlet.log.LogSupport

  import java.time.LocalDateTime
  import scala.concurrent.duration.Duration
  import scala.jdk.CollectionConverters.*
  
  case class FreeProxy(ipAddress: String,
                       port: Int,
                       code: String,
                       country: String,
                       anonymity: String,
                       google: String,
                       https: String,
                       lastChecked: LocalDateTime)


  object FreeProxy extends LogSupport {
    def apply(csvRecord: CSVRecord): IO[FreeProxy] = IO {
//      FreeProxy(
//          csvRecord.get("ipAddress"),
//          csvRecord.get("port").toInt,
//          csvRecord.get("code"),
//          csvRecord.get("country"),
//          csvRecord.get("anonymity"),
//          csvRecord.get("google"),
//          csvRecord.get("https"),
////          csvRecord.get("lastChecked")
//          LocalDateTime.now()
//      )
      FreeProxy(
        csvRecord.get(0),
        csvRecord.get(1).toInt,
        csvRecord.get(2),
        csvRecord.get(3),
        csvRecord.get(4),
        csvRecord.get(5),
        csvRecord.get(6),
        //          csvRecord.get("lastChecked")
        LocalDateTime.now()
      )
    }

    def apply(element: WebElement): IO[List[FreeProxy]] =
      for {
        now: LocalDateTime <- IO(LocalDateTime.now())
        rows: List[List[String]] <- IO.blocking {
          element.findElements("tr").asScala.drop(1).toList
            .map { (tr: WebElement) => tr.findElements("td").asScala.map(_.getText).toList }
        }
        proxies <- rows.traverse { (ls: List[String]) =>
          IO {
            val durationString = ls(7).replace("ago", "")
            val lastChecked: LocalDateTime =
              """(\d+ \S+)""".r
                .findAllIn(durationString).toList
                .map(Duration.apply)
                .foldLeft(now) { case (t: LocalDateTime, d: Duration) =>
                  t.plus(java.time.Duration.ofNanos(d.toNanos))
                }
            FreeProxy(ls.head, ls(1).toInt, ls(2), ls(3), ls(4),
              ls(5), ls(6), lastChecked)
          }.recoverWith { case throwable: Throwable =>
            error(s"`${throwable.getMessage}` when parsing $ls")
            throw throwable
          }
        }
      } yield proxies
  }
