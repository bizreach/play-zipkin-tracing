package jp.co.bizreach.trace.akka.actor

import akka.actor.ActorSystem
import brave.Tracing
import brave.sampler.Sampler
import jp.co.bizreach.trace.{ZipkinTraceConfig, ZipkinTraceServiceLike}
import zipkin.reporter.AsyncReporter
import zipkin.reporter.okhttp3.OkHttpSender

import scala.concurrent.ExecutionContext

/**
 * Class for Zipkin tracing at standalone Akka Actor.
 *
 * @param actorSystem an actor system used for tracing
 * @param serviceName a service name (default is `"unknown"`)
 * @param baseUrl a base url of the Zipkin server (default is `"http://localhost:9411"`)
 * @param sampleRate a sampling rate (default is `None` which means `ALWAYS_SAMPLE`)
 */
class ZipkinTraceService(
  actorSystem: ActorSystem,
  serviceName: String = "unknown",
  baseUrl: String = "http://localhost:9411",
  sampleRate: Option[Float] = None) extends ZipkinTraceServiceLike {

  implicit val executionContext: ExecutionContext = actorSystem.dispatchers.lookup(ZipkinTraceConfig.AkkaName)

  val tracing = Tracing.newBuilder()
    .localServiceName(serviceName)
    .reporter(AsyncReporter.builder(OkHttpSender.create(baseUrl + "/api/v1/spans")).build())
    .sampler(sampleRate.map(x => Sampler.create(x)) getOrElse Sampler.ALWAYS_SAMPLE)
    .build()

}