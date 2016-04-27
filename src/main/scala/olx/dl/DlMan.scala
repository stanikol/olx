package olx.dl

import akka.actor.{Actor, ActorRef, Props, Terminated}
import akka.pattern.ask
import olx.{Adv, AdvUrls, Cfg, Error}
import org.joda.time.format.{DateTimeFormat, PeriodFormatterBuilder}
import org.joda.time.{DateTime, Period}
import org.openqa.selenium.WebDriver
import org.slf4j.{LoggerFactory, MDC}

import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}
import org.json4s.jackson.Serialization._
import org.json4s.DefaultFormats
import org.json4s.JsonAST.JValue

import scala.util.parsing.json.JSONObject

/**
  * Created by stanikol on 30.03.16.
  */


object DlMan {
  def props(target: String, poolSize: Int) = Props(classOf[DlMan], target, poolSize, DateTime.now())

  case class Start(url: String, maxAd: Int)

  case class DoWork()

  case class PoolItem(dl: ActorRef, work: Option[Dl.Work], startTime: Option[DateTime]) {
    def free = new PoolItem(dl, None, None)
  }

  object PoolItem {
    def apply(dl: ActorRef, work: Option[Dl.Work] = None): PoolItem = new PoolItem(dl, work, Some(DateTime.now()))
  }
}



class DlMan(target: String, poolSize: Int, startTime: DateTime) extends Actor{
  import olx.dl.DlMan._
  private val durationFormatter = new PeriodFormatterBuilder()
    .appendDays()
    .appendSuffix(" day", " days")
    .appendSeparator(" ")
    .appendHours()
    .appendSuffix(" hour", " hours")
    .appendSeparator(" ")
    .appendMinutes()
    .appendSuffix(" minute", " minutes")
    .appendSeparator(" ")
    .appendSeconds()
    .appendSuffix(" second", " seconds")
    .toFormatter
  private val log           = LoggerFactory.getLogger("MSG")
  private val saveAdvLogger = LoggerFactory.getLogger("SCRAP")
  private val storedURLs      = scala.collection.mutable.Set.empty[String]
  private val newURLs         = scala.collection.mutable.Set.empty[String]
  private val downloadedURLs  = scala.collection.mutable.Set.empty[String]
  private val errorURLs       = scala.collection.mutable.Set.empty[String]
  private val savePath: String = s"${Cfg.savedir.getAbsolutePath}/$target " +
                                    DateTime.now().toString(Cfg.save_path_time_suffix)
  private var workPool        = scala.collection.mutable.Set.empty[PoolItem]
  private var maxAdv: Int     = 0
  private var nextPage: Option[String] = None
  private var emptyPageCount: Int = 0

  import context.dispatcher
  private var pagesProcessed: Int = 0

  override def preStart() = {
    storedURLs ++= (scala.collection.mutable.Set.empty[String] ++ Adv.readStoredURLs(Cfg.savedir).get)
    1 to Cfg.number_of_fetchers foreach { i =>
      workPool += PoolItem(context.actorOf(Dl.propsChrome(self, target), name = s"AdvDl_$i" )) }
    workPool foreach{ pi:PoolItem => context.watch(pi.dl) }
  }

  def receive: Receive = {
    case Start(url: String, maxAdvert: Int) =>
      log.info(s"\nGot command to download $maxAdvert ads from $url")
      maxAdv = maxAdvert
      nextPage = Some(url)
      self ! DoWork

    case DoWork =>
      val sender_ = sender()
      log.info(
        s"\nTime elapsed $timeElapsed - ~ $timeFor1Adv for 1 adv; ${downloadedURLs.toList.length} ads have been already downloaded. ${errorURLs.toList.length} urls failed. " +
        s"\nHave ${newURLs.toList.length} new ad urls plus ${workPool.count(_.work.isDefined)} ads are downloading now. pagesProcessed = $pagesProcessed; emptyPageCount = $emptyPageCount;" +
        s"\nnextPage = ${nextPage.getOrElse("None")};\n")
      val workPollIsFree = workPool.forall(_.work.isEmpty)
//      if(nextPage.isDefined && newURLs.isEmpty && workPollIsFree){
      if(nextPage.isDefined && newURLs.toList.length < workPool.filter(_.work.isEmpty).toList.length - 1){
        val nextPageURL = nextPage.get
        val advLinksF: Future[AdvUrls] =
          ask(workPool.head.dl, Dl.Work({wd: WebDriver=> AdvUrls(nextPageURL)(wd)}, nextPageURL))(Cfg.kill_actor_when_no_response).mapTo[AdvUrls]
        Try{ Await.result(advLinksF, Cfg.kill_actor_when_no_response ) } match {
          case Success(AdvUrls(links, nextPg)) =>
            nextPage = if(emptyPageCount < Cfg.empty_page_stop) nextPg else None
            pagesProcessed += 1
            val newFoundURLs = links.filter { l =>
              !storedURLs.contains(l) &
                !downloadedURLs.contains(l) &
                  !newURLs.contains(l) &
                    !errorURLs.contains(l)
            }
            if (newFoundURLs.isEmpty)
              emptyPageCount += 1
            else
              emptyPageCount = 0
            log.info(s"\nFound ${newFoundURLs.length} new links on $nextPageURL. \nnextPage = $nextPage")
            newURLs ++= newFoundURLs
          case Failure(error) =>
            log.error(s"\n#ERROR while getting links from $nextPageURL\n${error.getMessage}")
//            nextPage = None
        }
      }
      if(newURLs.nonEmpty){
        val updatedWorkPool =
          for(pi <- workPool)
            yield
              if(pi.work.isDefined)
                pi
              else {
                newURLs.headOption match {
                  case Some(url) =>
                    newURLs -= url
                    val newPoolItem = PoolItem(pi.dl, advDownloadFactory(url))
                    newPoolItem.dl ! newPoolItem.work.get
                    newPoolItem
                  case _ => pi
                }
              }
        workPool = updatedWorkPool
      }
      if(newURLs.isEmpty && nextPage.isEmpty && workPool.forall(_.work.isEmpty)){
        log.info(s"\nWork is done. ${downloadedURLs.toList.length} ads are downloaded.")
        context.system.terminate()
      }
      val downURLs = downloadedURLs.toList.length
      if(downURLs >= maxAdv){
        log.info(s"\nWork is done. All $maxAdv of $downURLs ads are downloaded.")
        context.system.terminate()
      }
      context.system.scheduler.scheduleOnce(Cfg.sleep_time) {
        self ! DoWork
      }

    case  adv @ Adv(items) =>
      val sender_ = sender
      assert(workPool.exists(_.dl == sender_))
      val pi = workPool.find(_.dl == sender_).get
      saveAdv(adv)
      workPool -= pi
      newURLs.headOption match {
        case None => workPool += pi.free
        case Some(url) =>
            newURLs -= url
            val newPoolItem = PoolItem(pi.dl, advDownloadFactory(url))
            newPoolItem.dl ! newPoolItem.work.get
            workPool += newPoolItem
      }

    case errorURL @ Error(url, error) =>
      val sender_ = sender
      val errorMessage = error.getMessage
      log.error(s"\n#ERROR from ${sender_.path.name} fetching $url\n${errorMessage}")
      errorURLs += Adv.stripURL(url)
      assert(workPool.exists(_.dl == sender_))
      val failedDl: PoolItem = workPool.find(_.dl == sender_).get
      workPool -= failedDl
      workPool += failedDl.copy(work = None, startTime = None)
//      workPool += PoolItem(context.actorOf(Dl.propsChrome(self, target), name = s"${failedDl.dl.path.name}r" ))
//      context.stop(failedDl.dl)
      MDC.put("savePath", "failed_urls")
      implicit val formats = org.json4s.DefaultFormats
      val errorAsJSON = writePretty(Map("url"->url, "error"->errorMessage))
      log.info(errorAsJSON)
      MDC.put("savePath", savePath)

    case Terminated(actor: ActorRef) =>
      workPool.retain(_.dl != actor)

    case x =>
      val senderActor = sender.path.name
      log.error(s"\nDlMan $target: Got unknown command $x from $senderActor")
  }

  def saveAdv(adv: Adv) = {
    downloadedURLs += adv.url
    val Adv(items) = adv
    val updatedItems = items.updated("filename", new java.io.File(savePath).getName).updated("target", target)
    MDC.put("savePath", savePath)
    saveAdvLogger.info(Adv(updatedItems).toString())
  }

  def advDownloadFactory(url: String): Some[Dl.Work] = {
    val work = Dl.Work(
      { wd: WebDriver =>
        try {
          val adv = Adv(url)(wd)
          wd.get("about:blank")
          adv
        } catch {
          case error: Throwable => Error(url, error)
        }
      },
      url)
    Some(work)
  }

  def timeFor1Adv: String = Try {
    val millis: Long = new Period(startTime, DateTime.now()).toStandardDuration.getMillis
    durationFormatter.print(
      new Period(millis / downloadedURLs.toList.length).normalizedStandard()
    )
  }.getOrElse("0 seconds")

  def timeElapsed: String = {
    val period = new Period(startTime, DateTime.now)
    durationFormatter.print(period)
  }
}
