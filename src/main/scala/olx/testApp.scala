package olx

import java.io.File
import java.util
import java.util.concurrent.Future

import com.asprise.ocr.{Ocr, OcrExecutorService}

import scala.collection.mutable.ArrayBuffer
import collection.JavaConverters._
import collection.mutable._

/**
  * Created by stanikol on 08.05.16.
  */
object testApp extends App{

  val oes =
    new OcrExecutorService("eng", Ocr.SPEED_FASTEST, 4) // 4 threads

  val files: Array[File] = Array(new File("/Users/snc/tmp/lsmith.png"))
  val futures = oes.invokeAll(util.Arrays.asList(
    new OcrExecutorService.OcrCallable(
     files,
      Ocr.RECOGNIZE_TYPE_TEXT, Ocr.OUTPUT_FORMAT_PLAINTEXT)
//    ,
//    new OcrExecutorService.OcrCallable(
//      new File[] {new File("test2.png")},
//      Ocr.RECOGNIZE_TYPE_ALL, Ocr.OUTPUT_FORMAT_XML)
  ))

  println("Result of test1.png: " + futures.get(0).get())
//  println("Result of test2.png: " + futures.get(1).get())



}
