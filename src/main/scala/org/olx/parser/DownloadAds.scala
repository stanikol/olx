package org.olx.parser

import cats.effect.IO
import fs2.Stream
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.olx.db.H2
import wvlet.log.LogSupport

import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import scala.util.Try

trait DownloadAds extends LogSupport
  with DownloadLinks
  with HttpClient
  with H2:

  def downloadAndSaveAds(s: Search): IO[Unit] =
    transactor.use { transactor =>
      downloadAds(s).through(saveToDb(transactor, s)).compile.drain >>
        IO(info(s"downloadAndSaveAds: [OK] Completed downloads: $s"))
    }

  private def downloadAds(search: Search): Stream[IO, Advertisement] =
    downloadLinks(search.downloadAdsCount, search.url.get)
      .take(search.downloadAdsCount)
      .evalMap(downloadAd)

  private def downloadAd(href: String): IO[Advertisement] =
    httpClient.use { c =>
      val start: Long = System.nanoTime()
      debug(s"downloadAd: Requesting $href ...")
      c.expect[String](href)
        .map { response =>
          val elapsedNano: Long = System.nanoTime() - start
          val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNano)
          info(s"downloadAd: Ad from '$href' was downloaded in $elapsedMillis ms.")
          response
        }
    } flatMap parseAd(href)

  private def parseAd(href: String)(html: String): IO[Advertisement] = IO.blocking {
    debug(s"parseAd: Parsing $href ...")
    val doc = Jsoup.parse(html)
    val title: String = doc.select("div[data-cy='ad_title']").text()
    val description: String = doc.select("div[data-cy='ad_title']").text()
    val price: String = doc.select("div[data-testid='ad-price-container']").text()
    val brief: Map[String, String] = doc.select("div#mainContent ul>li>p").asScala
      .map { (e: Element) =>
        val text = e.text().split(":")
        text(0) -> Try(text(1)).getOrElse("true")
      }
      .filter { case (k, v) => k.nonEmpty && v.nonEmpty }.toMap
    val ad = Advertisement(href, title, description, price, brief, doc.html())
    debug(s"parseAd: [OK] Finished parsing $href")
    ad
  }

end DownloadAds


