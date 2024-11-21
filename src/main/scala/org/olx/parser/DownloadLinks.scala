package org.olx.parser

import cats.effect.*
import fs2.Stream
import org.http4s.Uri
import org.http4s.implicits.uri
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.openqa.selenium.remote.RemoteWebDriver
import wvlet.log.{LogLevel, LogSupport}

import scala.jdk.CollectionConverters.*

trait DownloadLinks extends LogSupport {
  self: HttpClient =>

  def downloadLinks(number: Int, url: String): Stream[IO, String] = {
    Stream.unfoldEval(number) { (leftToDownload: Int) =>
      if (leftToDownload > 0)
        for {
          links: Links <- downloadLinksFromPage(url)
          left: Int = math.max(0, leftToDownload - links.ads.length)
          next: Option[(Links, Int)] <- if (links.nextPage.isEmpty) {
            IO(info(s"downloadLinks: $left from $number links left (but next page link is empty).")) >>
              // IO.pure(None)
              IO.pure(Some(links -> left))
          } else {
            IO(info(s"downloadLinks: $left from $number links left to download.")) >>
              IO.pure(Some(links -> left))
          }}
        yield next
      else IO.pure(None)
    }
  }.flatMap { (l: Links) => Stream.emits(l.ads) }

  private def downloadLinksFromPage(url: String): IO[Links] =
    httpClient.use { c =>
      val cssNextPage = "a[data-cy='pagination-forward'][data-testid='pagination-forward']"
      val cssAdsHref = "div[data-testid='l-card'][data-cy='l-card'] a"
      for {
        html: String <- c.expect[String](url)
        doc: Document <- IO(Jsoup.parse(html))
        nextPage: Option[String] <- IO.blocking(doc.select(cssNextPage).attr("href"))
          .map { case "" => None; case s: String => Some(s) }
          .recover { case error => None }
        adsHref: List[String] <- IO.blocking(doc.select(cssAdsHref).asScala.map(_.attr("href")).toList)
        uri: Uri <- IO(Uri.fromString(url).getOrElse(uri"https://olx.ua/"))
        addHostToParsedLink = { (p: String) => uri.withPath(Uri.Path.unsafeFromString(p)).toString }
        links = Links(adsHref.distinct.map(addHostToParsedLink), nextPage)
        _ <- IO(info(s"downloadLinksFromPage: Downloaded ${links.ads.length} links from $url."))
      } yield links
    }


  protected def openOlx(wd: RemoteWebDriver): IO[RemoteWebDriver] =
    for {
      _ <- IO.blocking(wd.get("http://www.olx.ua"))
      _ <- IO.println("OLX is ready.")
    } yield wd

}
