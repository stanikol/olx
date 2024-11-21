package org.olx.parser

import cats.effect.{IO, Resource}
import org.openqa.selenium.PageLoadStrategy
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.remote.RemoteWebDriver
import wvlet.log.{LogLevel, LogSupport}

import java.net.URL

object WD extends LogSupport {

  def makeWebDriver(proxy: Option[String] = None): Resource[IO, RemoteWebDriver] =
    Resource.make(mkWebDriver(proxy))(closeWebDriver)

  private def mkWebDriver(proxyOpt: Option[String] = None): IO[RemoteWebDriver] = IO.blocking {
    info("mkWebDriver: Starting FireFox ...")
    val firefoxOptions = new FirefoxOptions()
    proxyOpt.foreach { proxy =>
      info(s"mkWebDriver: Using SSL proxy $proxy ...")
      val seleniumProxy = new org.openqa.selenium.Proxy
      seleniumProxy.setHttpProxy(proxy)
      seleniumProxy.setSslProxy(proxy)
      firefoxOptions.setProxy(seleniumProxy)
    }
    firefoxOptions.setPageLoadStrategy(PageLoadStrategy.EAGER)
    val remoteAddress = new URL("http://webdriver:4444")
//    val remoteAddress = new URL("http://localhost:4444") //Change to run locally
    val wd = new RemoteWebDriver(remoteAddress, firefoxOptions)
    info(s"mkWebDriver: [OK] New WebDriver is successfully created (remoteAddress=${remoteAddress.getRef}).")
    wd
  }

  private def closeWebDriver(wd: RemoteWebDriver): IO[Unit] =
    IO.blocking {
      wd.quit()
      info("closeWebDriver: [OK] WebDriver is closed.")
    }

}