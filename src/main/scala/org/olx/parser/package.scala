package org.olx

import org.http4s.UrlForm
import org.openqa.selenium.By
import cats.syntax.all._
import java.time.LocalDateTime
import scala.util.Try

package object parser:

  case class Links(ads: List[String], nextPage: Option[String])

  case class Advertisement(href: String, title: String, description: String, price: String,
                           brief: Map[String, String], html: String) {
    override def toString: String = s"Advertisement($href, $title, $price, ...)"
  }

  case class Search(name: String, url: Option[String], downloadAdsCount: Int, time: LocalDateTime)

  object Search {
    def fromUrlForm(uf: UrlForm): Option[Search] =
      def read(field: String): Option[String] =
        uf.values.collectFirst { case (k, v) if k == field => v }.flatMap(_.headOption)
      val url: Option[String] = read("url").map(_.strip)
      val count: Option[Int] = read("count").flatMap(c=>Try(c.toInt).toOption)
      val search: Option[Search] = (read("name"), count)
        .mapN { (name: String, count: Int) =>
          Search(name, url, count, LocalDateTime.now())
        }
      search
    end fromUrlForm
    
  }

  given StrToCssSelector: Conversion[String, By] with {
    override def apply(x: String): By = By.cssSelector(x)
  }

end parser
