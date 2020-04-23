/*
 * Smart Data Lake - Build your data lake the smart way.
 *
 * Copyright © 2019-2020 ELCA Informatique SA (<https://www.elca.ch>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package io.smartdatalake.workflow.dataobject

import com.typesafe.config.Config
import io.smartdatalake.config.SdlConfigObject.{ConnectionId, DataObjectId}
import io.smartdatalake.config.{FromConfigFactory, InstanceRegistry}
import io.smartdatalake.util.hdfs.SparkRepartitionDef
import io.smartdatalake.util.misc.AclDef
import io.smartdatalake.util.misc.DataFrameUtil.DfSDL
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.{DataFrame, SaveMode}

/**
 * A [[io.smartdatalake.workflow.dataobject.DataObject]] backed by a JSON data source.
 *
 * It manages read and write access and configurations required for [[io.smartdatalake.workflow.action.Action]]s to
 * work on JSON formatted files.
 *
 * Reading and writing details are delegated to Apache Spark [[org.apache.spark.sql.DataFrameReader]]
 * and [[org.apache.spark.sql.DataFrameWriter]] respectively.
 *
 * @param stringify Set the data type for all values to string.
 * @param jsonOptions Settings for the underlying [[org.apache.spark.sql.DataFrameReader]] and
 *                    [[org.apache.spark.sql.DataFrameWriter]].
 * @param sparkRepartition Optional definition of repartition operation before writing DataFrame with Spark to Hadoop.
 *
 * @note By default, the JSON option `multiline` is enabled.
 *
 * @see [[org.apache.spark.sql.DataFrameReader]]
 * @see [[org.apache.spark.sql.DataFrameWriter]]
 */
case class JsonFileDataObject( override val id: DataObjectId,
                               override val path: String,
                               jsonOptions: Option[Map[String, String]] = None,
                               override val partitions: Seq[String] = Seq(),
                               override val schema: Option[StructType] = None,
                               override val schemaMin: Option[StructType] = None,
                               override val saveMode: SaveMode = SaveMode.Overwrite,
                               override val sparkRepartition: Option[SparkRepartitionDef] = None,
                               stringify: Boolean = false,
                               override val acl: Option[AclDef] = None,
                               override val connectionId: Option[ConnectionId] = None,
                               override val metadata: Option[DataObjectMetadata] = None
                             )(implicit override val instanceRegistry: InstanceRegistry)
  extends SparkFileDataObject with CanCreateDataFrame with CanWriteDataFrame {

  override val format = "json"

  // this is only needed for FileRef actions
  override val fileName: String = "*.json*"

  private val formatOptionsDefault = Map("multiLine" -> "true")
  override val options: Map[String, String] = formatOptionsDefault ++ jsonOptions.getOrElse(Map())

  override def afterRead(df: DataFrame): DataFrame  = {
    val dfSuper = super.afterRead(df)
    if (stringify) dfSuper.castAll2String else dfSuper
  }

  override def beforeWrite(df: DataFrame): DataFrame  = {
    val dfSuper = super.beforeWrite(df)
    if (stringify) dfSuper.castAll2String else dfSuper
  }

  /**
   * @inheritdoc
   */
  override def factory: FromConfigFactory[DataObject] = JsonFileDataObject
}

object JsonFileDataObject extends FromConfigFactory[DataObject] {

  /**
   * @inheritdoc
   */
  override def fromConfig(config: Config, instanceRegistry: InstanceRegistry): JsonFileDataObject = {
    import configs.syntax.ConfigOps
    import io.smartdatalake.config._

    implicit val instanceRegistryImpl: InstanceRegistry = instanceRegistry
    config.extract[JsonFileDataObject].value
  }
}
