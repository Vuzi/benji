/*
 * Copyright (C) 2018-2019 Zengularity SA (FaberNovel Technologies) <https://www.zengularity.com>
 */

package com.zengularity.benji

import scala.language.higherKinds

import scala.collection.generic.CanBuildFrom
import scala.concurrent.{ ExecutionContext, Future }

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }

/**
 * A live reference to a storage bucket.
 * The operations are scoped on the specified bucket.
 *
 * Such reference must only be used with the storage which resolved it first.
 */
trait BucketRef {
  /**
   * The name of the bucket.
   */
  def name: String

  /**
   * Prepares a request to list the bucket objects.
   *
   * {{{
   * def enumObjects(b: BucketRef) = b.objects()
   *
   * def objectList(b: BucketRef) = b.objects.collect[List]()
   * }}}
   */
  def objects: ListRequest

  /**
   * Determines whether or not the bucket exists.
   * `false` might be returned also in cases where you don't have permission
   * to view a certain bucket.
   *
   * {{{
   * def check(store: ObjectStorage, name: String)(
   *   implicit ec: ExecutionContext): Future[Boolean] =
   *   store.bucket(name).exists
   * }}}
   */
  def exists(implicit ec: ExecutionContext): Future[Boolean]

  /**
   * Creates the bucket, if the bucket already exists operation will succeed unless parameter failsIfExists is true.
   *
   * @param failsIfExists if true, the future will be failed if the bucket already exists.
   * @note On some module configuring failsIfExists to true may slow down this operation.
   *
   * {{{
   * def setupBucket(store: ObjectStorage, name: String)(
   *   implicit ec: ExecutionContext): Future[BucketRef] = {
   *   // Make sure a bucket is available (either a new or existing one)
   *   val bucket = store.bucket(name)
   *   bucket.create(failsIfExists = false).map(_ => bucket)
   * }
   * }}}
   */
  def create(failsIfExists: Boolean = false)(implicit ec: ExecutionContext): Future[Unit]

  /**
   * Prepares a request to delete the referenced bucket.
   *
   * {{{
   * myBucket.delete() // .delete.apply()
   * myBucket.delete.ignoreIfNotExists() // delete.ignoreIfNotExists.apply()
   * }}}
   */
  def delete: DeleteRequest

  /**
   * Returns a reference to an child object, specified by the given name.
   *
   * @param objectName the name of child object
   *
   * {{{
   * myBucket.obj("objInMyBucket").exists()
   * }}}
   */
  def obj(objectName: String): ObjectRef

  /**
   * Try to get a reference of this bucket that would allow
   * to perform versioning related operations.
   *
   * @return Some if the module of the bucket supports versioning
   * related operations, otherwise None.
   *
   * {{{
   * myBucket.versioning.map(_.isVersioned)
   * }}}
   */
  def versioning: Option[BucketVersioning]

  // ---

  /**
   * A request to list the objects inside this bucket.
   */
  trait ListRequest {
    /**
     * Lists of the matching objects within the bucket.
     *
     * {{{
     * bucketRef.objects()
     * }}}
     */
    def apply()(implicit m: Materializer): Source[Object, NotUsed]

    /**
     * Collects the matching objects.
     *
     * {{{
     * bucketRef.objects.collect[List]()
     * }}}
     */
    final def collect[M[_]]()(implicit m: Materializer, builder: CanBuildFrom[M[_], Object, M[Object]]): Future[M[Object]] = {
      implicit def ec: ExecutionContext = m.executionContext

      apply() runWith Sink.fold(builder()) {
        _ += (_: Object)
      }.mapMaterializedValue(_.map(_.result()))
    }

    /**
     * Defines batch size for retrieving objects with multiple requests.
     *
     * @param max the maximum number of objects fetch at once
     *
     * {{{
     * bucketRef.objects.withBatchSize(10L).collect[Set]()
     * }}}
     */
    def withBatchSize(max: Long): ListRequest

    /**
     * Defines the prefix the listed objects must match.
     *
     * {{{
     * bucketRef.objects.withPrefix("foo").collect[Set]()
     * }}}
     */
    def withPrefix(prefix: String): ListRequest
  }

  /**
   * A request to delete the bucket.
   */
  trait DeleteRequest {
    /**
     * Deletes the current bucket.
     *
     * {{{
     * myBucket.delete()
     * }}}
     */
    def apply()(implicit m: Materializer): Future[Unit]

    /**
     * Updates the request, so that it will not raise an error
     * if the referenced bucket doesn't exist when executed.
     *
     * {{{
     * myBucket.delete.ignoreIfNotExists()
     * myBucket.delete.ignoreIfNotExists.recursive()
     * }}}
     */
    def ignoreIfNotExists: DeleteRequest

    /**
     * Updates the request so that it will succeed on non-empty bucket,
     * by also deleting all of its content, this includes its objects and
     * if applicable also all versions of the objects.
     *
     * {{{
     * myBucket.delete.recursive()
     * myBucket.delete.recursive.ignoreIfNotExists()
     * }}}
     */
    def recursive: DeleteRequest
  }
}
