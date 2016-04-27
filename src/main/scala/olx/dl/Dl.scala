package olx.dl

import java.io.File
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Props}
import olx.{Cfg, Data}
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.firefox.{FirefoxBinary, FirefoxDriver, FirefoxProfile}
import org.openqa.selenium.htmlunit.HtmlUnitDriver
import org.openqa.selenium.safari.SafariDriver
import org.slf4j.LoggerFactory

/**
  * Created by stanikol on 01.04.16.
  */
object Dl {

  def propsChrome(dlManager: ActorRef, target: String): Props =
    Props(classOf[Dl[ChromeDriver]], dlManager, target, () => newChromeDriver)

  private def newChromeDriver: WebDriver = {

//    val drv = new FirefoxDriver()
//    System.setProperty("profile.default_content_settings.images", "2")

    val drv = new ChromeDriver()
    drv.manage().timeouts().pageLoadTimeout(Cfg.kill_actor_when_no_response.toSeconds, TimeUnit.SECONDS)
//    drv.manage().timeouts().implicitlyWait(1, TimeUnit.SECONDS)
    drv
  }

  case class Work(work: WebDriver => Data, url: String)

}


import olx.dl.Dl._

class Dl[WD<: WebDriver](dlMan: ActorRef, target: String, webDriverFactory: => WD) extends Actor {

  val log = LoggerFactory.getLogger("MSG")
  implicit var webDriver: WD        = _

  override def preStart = {
    if(webDriver != null) webDriver.close()
    webDriver = webDriverFactory
  }

  override def postStop = {
    if(webDriver != null) webDriver.close()
  }

  def receive: Receive = {
    case Work(work, link) =>
      val snd = sender()
      log.trace(s"\n${self.path.name}: received Work at $link")
      snd ! work(webDriver)
      log.trace(s"\n${self.path.name}: finished Work at $link")
  }


}
