package olx.down

import akka.actor.{Actor, ActorRef}
import olx.Log._
import olx._
import olx.down.scrap.{OLXScrapWS, ScrapWS}
import org.joda.time.{DateTime, Period}
import play.api.libs.ws.ahc

import scala.collection.mutable.{Set => MutebleSet}
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
  * Created by stanikol on 21.04.16.
  */
object DownloadManagerWS {
  case class Tick()
  case class DownloadUrl(url: String, target: String)
  case class DownloadUrls(urls: Stream[String], target: String)

  case class Finished(target: String, fetchedCount: Int, pagesCount: Int)
}

abstract class DownloadManagerWS extends Actor  with ScrapWS {

  case class JobPoolItem(job: Option[(String, Future[Data])]=None) {
    def isFree = job.isEmpty
    def url: Option[String] = job match { case None => None; case Some((url, f)) => Some(url) }
  }
  // TODO stop when some predifined number of pages without new links occurs
  private val jobPool = scala.collection.mutable.ListBuffer.empty[JobPoolItem]

  import context.dispatcher
  import olx.down.DownloadManagerWS._
  implicit val materializer = akka.stream.ActorMaterializer()
  implicit val wsClient = ahc.AhcWSClient()
  private var target: String = "Default"

  private var nextPage: Option[String] = None
  private val newLinks = MutebleSet.empty[String]
  private val fetchedLinks = MutebleSet.empty[String]
  private val storedLinks = MutebleSet.empty[String]
  private val errorLinks = MutebleSet.empty[String]
  private val visitedPages = MutebleSet.empty[String]
  private var retriesWhenSameNextPageOccurs: Int = Cfg.retries_when_same_next_page_occurs
  private var numberOfPagesWithoutNewLinks: Int = Cfg.number_of_pages_without_new_links
  private var masterActor: ActorRef = _
  private val startTime: DateTime = DateTime.now()


  override def preStart = {
    storedLinks ++= Adv.readStoredURLs(Cfg.savedir).get
  }

  private def receiveIdle: Receive = {
    case cmd @ DownloadUrl(url, target) =>
      rootLogger.info(s"${self.path.name} received $cmd")
      masterActor = sender()
      this.target = target
      rootLogger.info(s"`$target` -> $url")
      nextPage = Some(url)
      retriesWhenSameNextPageOccurs = Cfg.retries_when_same_next_page_occurs
      numberOfPagesWithoutNewLinks = Cfg.number_of_pages_without_new_links
      visitedPages.clear
      context.become(receiveBusy)
      self ! Tick()
    case cmd @ DownloadUrls(urls, target_) =>
      rootLogger.info(s"${self.path.name} received $cmd")
      masterActor = sender()
      target = target_
      rootLogger.info(s"`$target` -> ${urls.length}")
      newLinks.clear
      newLinks ++= urls
      nextPage = None
      retriesWhenSameNextPageOccurs = Cfg.retries_when_same_next_page_occurs
      numberOfPagesWithoutNewLinks = Cfg.number_of_pages_without_new_links
      visitedPages.clear
      context.become(receiveBusy)
      self ! Tick()
  }

  override def receive: Receive = receiveIdle

  private def receiveBusy: Receive = {
    case cmd @ (_:DownloadUrl | _:DownloadUrls) =>
      rootLogger.info(s"${self.path.name} error: received command $cmd, but I'm busy !")
    case Tick() =>
      printInfo
      fetchNextPageAndLinksIfReady
      fetchAdsIfFree
//      if((nextPage.isEmpty  && jobPool.isEmpty && newLinks.isEmpty ) |
//            retriesWhenSameNextPageOccurs <= 0 | numberOfPagesWithoutNewLinks <= 0) {
//        context.become(receiveIdle)
//        masterActor ! Finished(target, fetchedLinks.toList.length, visitedPages.toList.length)
//        newLinks.clear
//        errorLinks.clear
//        visitedPages.clear
//        retriesWhenSameNextPageOccurs = Cfg.retries_when_same_next_page_occurs
//        nextPage = None
//        numberOfPagesWithoutNewLinks = Cfg.number_of_pages_without_new_links
//      } else {
//        context.system.scheduler.scheduleOnce(Cfg.tick_time) { self ! Tick() }
//      }
      context.system.scheduler.scheduleOnce(Cfg.tick_time) { self ! Tick() }

    case adv @ Adv(items) =>
      val url = items("url")
      fetchedLinks += url
      freeJobPoolFrom(url)
      saveAdv(adv)
      fetchAdsIfFree
      fetchNextPageAndLinksIfReady
    case advUrls @ AdvUrls(urls, currentPage, nextPg) =>
      val newFoundLinks = urls.filter(url =>
          !(newLinks.contains(url) || storedLinks.contains(url) ||
          fetchedLinks.contains(url) || errorLinks.contains(url))
      )
      if( visitedPages.contains(nextPg.getOrElse("")) ) retriesWhenSameNextPageOccurs -= 1
      if(newFoundLinks.isEmpty) numberOfPagesWithoutNewLinks -= 1
      else numberOfPagesWithoutNewLinks = Cfg.number_of_pages_without_new_links
      rootLogger.info(s"`$target` ${newFoundLinks.length} new links from ${urls.length} are found on ${currentPage}.\n" +
                      s"`$target` numberOfPagesWithoutNewLinks = ${numberOfPagesWithoutNewLinks}")
      visitedPages += currentPage
      newLinks ++= newFoundLinks
      nextPage = nextPg
      freeJobPoolFrom(currentPage)
      fetchNextPageAndLinksIfReady
      fetchAdsIfFree
    case Error(url, error) =>
      val sender_ = sender()
      freeJobPoolFrom(url)
      errorLinks += url
      rootLogger.info(s"`$target` ${sender_.path.name} got error with $url: ${error}")
      fetchNextPageAndLinksIfReady
      fetchAdsIfFree

    case unknown =>
      rootLogger.error(s"Unknown command $unknown.")
  }

  private def fetchNextPageAndLinksIfReady: Unit = {
    if(!visitedPages.contains(nextPage.getOrElse("")) && nextPage.isDefined && jobPool.isEmpty
      && newLinks.isEmpty && retriesWhenSameNextPageOccurs >= 1 && numberOfPagesWithoutNewLinks >= 1) {
          val nextPageUrl = this.nextPage.get
          rootLogger.info(s"Going to fetch ad links from ${nextPageUrl}")
          val query = fetchAdvUrls(nextPageUrl)
          query.onComplete({
            case Success(advUrls@ AdvUrls(links, currentPage, nextPage)) =>
              self ! advUrls
              rootLogger.info(s"Successfully fetched ${links.length} links from $currentPage. Next page is ${nextPage.getOrElse("None")}")
            case Failure(error: Throwable) =>
              self ! Error(nextPageUrl, error.getMessage)
              rootLogger.error(s"Failure fetching links from $nextPageUrl: ${error.getMessage}")
            case Success(error @ Error(url, errorMsg)) =>
              self ! error
              rootLogger.error(s"Error fetching links from $url: $errorMsg.")
          })
          jobPool += JobPoolItem(Some(nextPageUrl->query))
    } else if((nextPage.isEmpty  && jobPool.isEmpty && newLinks.isEmpty ) ||
                  retriesWhenSameNextPageOccurs <= 0 || numberOfPagesWithoutNewLinks <= 0) {
                    context.become(receiveIdle)
                    val finished = Finished(target, fetchedLinks.toList.length, visitedPages.toList.length)
                    masterActor ! finished
                    rootLogger.info(s"Finished $finished.")
                    newLinks.clear
                    errorLinks.clear
                    visitedPages.clear
                    retriesWhenSameNextPageOccurs = Cfg.retries_when_same_next_page_occurs
                    nextPage = None
                    numberOfPagesWithoutNewLinks = Cfg.number_of_fetchers
    }
  }

  private def fetchAdsIfFree: Unit = {
    while(jobPool.length <= Cfg.number_of_fetchers && newLinks.nonEmpty){
      val adUrl = newLinks.head
      newLinks -= adUrl
      val query: Future[Data] = fetchAdv(adUrl)
      query.onComplete({
        case complete =>
          complete match {
            case Success(adv@Adv(items)) =>
              self ! adv
              rootLogger.info(s"An ad from $adUrl is successfully scraped.")
            case Failure(error: Throwable) =>
              self ! Error(adUrl, error.getMessage)
              rootLogger.error(s"Failure while fetching an ad from $adUrl: ${error.getMessage}.")
            case Success(error @ Error(url, errorMsg)) =>
              rootLogger.error(s"Error while fetching an ad from  $url: $errorMsg.")
              self ! error
          }
      })
      jobPool += JobPoolItem(Some(adUrl->query))
      rootLogger.info(s"Got command to scrap an ad from $adUrl; newLinks = ${newLinks.toList.length}; jobPool.length = ${jobPool.length}.")
    }
  }

  private def savePath: String = s"${Cfg.savedir.getAbsolutePath}/$target " +
    DateTime.now().toString(Cfg.save_path_time_suffix)

  val saveAdv: Function1[Adv, Unit] = Log.saveAdv(_, savePath)

  private def printInfo = {
          def timeElapsed: String = {
            val period = new Period(startTime, DateTime.now)
            DurationFormatters.durationWithMillis.print(period)
          }
          def timeFor1Adv: String = Try {
            val millis: Long = new Period(startTime, DateTime.now()).toStandardDuration.getMillis
            DurationFormatters.durationWithMillis.print(
              new Period(millis / fetchedLinks.toList.length).normalizedStandard()
            )
          }.getOrElse("0 seconds")
    rootLogger.info(
      s"""
         |Info: Time elapsed $timeElapsed - ~ $timeFor1Adv for 1 adv
         |target = $target
         |newLinks = ${newLinks.toList.length}
         |fetchedLinks = ${fetchedLinks.toList.length}
         |errorLinks = ${errorLinks.toList.length}
         |nextPage = ${nextPage.getOrElse("None")}
         |retriesWhenSameNextPageOccurs = ${retriesWhenSameNextPageOccurs}
         |visitedPages = ${visitedPages.toList.length}
         |jobPool = ${jobPool.filter(_.isFree).toList.length} free of ${jobPool.toList.length}
         |jobPoolURLs = ${jobPool.filter(!_.isFree).map(_.url.get).mkString(", ")}
         """.stripMargin)

  }

  private def freeJobPoolFrom(url: String) = {
    val jobPoolItem = jobPool.find(_.url.getOrElse("") == url).get
    jobPool -= jobPoolItem
  }



}
