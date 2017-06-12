package jp.co.bizreach.trace.play24

import javax.inject.Inject

import akka.actor.ActorSystem
import brave.Tracing
import brave.sampler.Sampler
import jp.co.bizreach.trace.{ZipkinTraceServiceLike, ZipkinTraceConfig}
import play.api.Configuration
import zipkin.reporter.AsyncReporter
import zipkin.reporter.okhttp3.OkHttpSender

import scala.concurrent.ExecutionContext

/**
 * Class for Zipkin tracing at Play2.4.
 *
 * @param conf a Play's configuration
 * @param actorSystem a Play's actor system
 */
class ZipkinTraceService @Inject() (
  conf: Configuration,
  actorSystem: ActorSystem) extends ZipkinTraceServiceLike {

  implicit val executionContext: ExecutionContext = actorSystem.dispatchers.lookup(ZipkinTraceConfig.AkkaName)

  val tracing = Tracing.newBuilder()
    .localServiceName(conf.getString(ZipkinTraceConfig.ServiceName) getOrElse "unknown")
    .reporter(AsyncReporter
      .builder(OkHttpSender.create(
        s"${conf.getString(ZipkinTraceConfig.ZipkinProtocol) getOrElse "http"}://${conf.getString(ZipkinTraceConfig.ZipkinHost) getOrElse "localhost"}:${conf.getInt(ZipkinTraceConfig.ZipkinPort) getOrElse 9411}/api/v1/spans"
      ))
      .build()
    )
    .sampler(conf.getString(ZipkinTraceConfig.ZipkinSampleRate)
      .map(s => Sampler.create(s.toFloat)) getOrElse Sampler.ALWAYS_SAMPLE
    )
    .build()

}
