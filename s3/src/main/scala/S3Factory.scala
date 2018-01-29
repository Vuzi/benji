package com.zengularity.benji.s3

import java.net.URI

import play.api.libs.ws.ahc.StandaloneAhcWSClient

import com.zengularity.benji.ObjectStorage

import com.zengularity.benji.spi.{ Injector, StorageFactory, StorageScheme }

/**
 * This factory is using `javax.inject`
 * to resolve [[play.api.libs.ws.StandaloneWSClient]].
 */
final class S3Factory extends StorageFactory {
  @SuppressWarnings(Array("TryGet"))
  def apply(injector: Injector, uri: URI): ObjectStorage = {
    @inline implicit def ws: StandaloneAhcWSClient =
      injector.instanceOf(classOf[StandaloneAhcWSClient])

    S3[URI](uri).get
  }
}

final class S3Scheme extends StorageScheme {
  val scheme = "s3"

  @inline
  def factoryClass: Class[_ <: StorageFactory] = classOf[S3Factory]
}
