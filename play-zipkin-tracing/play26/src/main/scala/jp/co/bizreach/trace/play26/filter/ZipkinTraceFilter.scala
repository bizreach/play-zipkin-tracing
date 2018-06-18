package jp.co.bizreach.trace.play26.filter

import javax.inject.Inject

import akka.stream.Materializer
import brave.http.{HttpServerAdapter, HttpServerHandler}
import brave.propagation.Propagation.Getter
import brave.propagation.TraceContext
import jp.co.bizreach.trace.ZipkinTraceServiceLike
import org.apache.commons.lang3.StringUtils
import play.api.mvc.{Filter, Headers, RequestHeader, Result}
import play.api.routing.Router
import zipkin2.Endpoint

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * A Zipkin filter.
 *
 * This filter is that reports how long a request takes to execute in Play as a server span.
 * The way to use this filter is following:
 * {{{
 * class Filters @Inject() (
 *   zipkin: ZipkinTraceFilter
 * ) extends DefaultHttpFilters(zipkin)
 * }}}
 *
 * @param tracer a Zipkin tracer
 * @param mat a materializer
 */
class ZipkinTraceFilter @Inject() (tracer: ZipkinTraceServiceLike)(implicit val mat: Materializer) extends Filter {

  import tracer.executionContext

  // TODO: any way to get rid of this asInstanceOf?
  private val handler: HttpServerHandler[RequestHeader, Result] =
    HttpServerHandler.create(tracer.httpTracing, new HttpAdapter)
      .asInstanceOf[HttpServerHandler[RequestHeader, Result]]
  private val extractor: TraceContext.Extractor[Headers] =
    tracer.httpTracing.tracing.propagation.extractor(ZipkinTraceFilter.headerGetter)

  def apply(nextFilter: (RequestHeader) => Future[Result])(req: RequestHeader): Future[Result] = {
    var template = req.attrs.get(Router.Attrs.HandlerDef).map(_.path)
    // TODO ^^ is always None! is it being read too soon?
    val span = handler.handleReceive(extractor, req.headers, req)

    // invoke the next filter with this span in scope
    val scope = tracer.tracing.currentTraceContext().newScope(span.context())
    val result = try {
      nextFilter(req)
    } finally {
      scope.close()
    }

    result.onComplete {
      case Failure(t) => handler.handleSend(null, t, span)
      case Success(r) =>
        // add synthetic headers for route-based naming
        handler.handleSend(r.withHeaders(
          "brave-http-method" -> req.method,
          "brave-http-template" -> template.map(t =>
            StringUtils.replace(t, "<[^/]+>", "")
          ).getOrElse("")
        ), null, span)
    }
    result
  }

  class HttpAdapter extends HttpServerAdapter[RequestHeader, Result] {
    override def method(req: RequestHeader) = req.method

    override def url(req: RequestHeader) =
      "http" + (if (req.secure) "s" else "") + "://" + req.host + req.uri

    override def requestHeader(req: RequestHeader, name: String) = req.headers.get(name).orNull

    override def methodFromResponse(response: Result): String =
      response.header.headers.apply("brave-http-method")

    override def route(response: Result): String =
      response.header.headers.apply("brave-http-template")

    override def statusCode(response: Result) = response.header.status

    override def parseClientAddress(req: RequestHeader, builder: Endpoint.Builder): Boolean = {
      if (super.parseClientAddress(req, builder)) return true
      builder.parseIp(req.remoteAddress)
    }
  }
}

object ZipkinTraceFilter {
  val headerGetter: Getter[Headers, String] = (headers, key) => headers.get(key).orNull
}
