package io.finch

import com.twitter.finagle.http.{Request, Status}
import com.twitter.util.{Await, Future}
import io.finch.internal.currentTime
import java.time.{ZonedDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter

class BootstrapSpec extends FinchSpec {

  behavior of "Bootstrap"

  it should "handle both Error and Errors" in {
    check { e: Either[Error, Errors] =>
      val exception = e.fold[Exception](identity, identity)

      val ee = Endpoint.liftAsync[Unit](Future.exception(exception))
      val rep = Await.result(ee.toServiceAs[Text.Plain].apply(Request()))
      rep.status === Status.BadRequest
    }
  }

  it should "respond 404 if endpoint is not matched" in {
    check { req: Request =>
      val s = Endpoint.empty[Unit].toServiceAs[Text.Plain]
      val rep = Await.result(s(req))

      rep.status === Status.NotFound
    }
  }

  it should "match the request version" in {
    check { req: Request =>
      val s = Endpoint.const(()).toServiceAs[Text.Plain]
      val rep = Await.result(s(req))

      rep.version === req.version
    }
  }

  it should "include Date header" in {
    val formatter = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC)
    def parseDate(s: String): Long = ZonedDateTime.parse(s, formatter).toEpochSecond

    check { (req: Request, include: Boolean) =>
      val s = Bootstrap.configure(includeDateHeader = include)
        .serve[Text.Plain](Endpoint.const(()))
        .toService

      val rep = Await.result(s(req))
      val now = parseDate(currentTime())

      (include && (parseDate(rep.date.get) - now).abs <= 1) ||
      (!include && rep.date.isEmpty)
    }
  }

  it should "include Server header" in {
    check { (req: Request, include: Boolean) =>
      val s = Bootstrap.configure(includeServerHeader = include)
        .serve[Text.Plain](Endpoint.const(()))
        .toService

      val rep = Await.result(s(req))

      (include && rep.server === Some("Finch")) ||
      (!include && rep.server.isEmpty)
    }
  }
}
