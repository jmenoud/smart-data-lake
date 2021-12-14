/*
 * Smart Data Lake - Build your data lake the smart way.
 *
 * Copyright © 2019-2021 Schweizerische Bundesbahnen SBB (<https://www.sbb.ch>)
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

package io.smartdatalake.workflow.action.snowparktransformer

import io.smartdatalake.config.ParsableFromConfig
import io.smartdatalake.config.SdlConfigObject.ActionId
import io.smartdatalake.smartdatalake.SnowparkDataFrame
import io.smartdatalake.workflow.ActionPipelineContext

trait SnowparkDfsTransformer {
  def name: String

  def description: Option[String]

  def prepare(actionId: ActionId)(implicit context: ActionPipelineContext): Unit = Unit

  def transform(actionId: ActionId, dfs: Map[String, SnowparkDataFrame])
               (implicit context: ActionPipelineContext): Map[String, SnowparkDataFrame]

  private[smartdatalake]
  def applyTransformation(actionId: ActionId, dfs: Map[String, SnowparkDataFrame])
                         (implicit context: ActionPipelineContext): Map[String, SnowparkDataFrame] = {
    transform(actionId, dfs)
  }
}

trait ParsableSnowparkDfsTransformer extends SnowparkDfsTransformer with ParsableFromConfig[ParsableSnowparkDfsTransformer]


trait OptionsDfsTransformer extends ParsableSnowparkDfsTransformer {
  def options: Map[String,String]
  def runtimeOptions: Map[String,String]

  /**
   * Function to be implemented to define the transformation between many inputs and many outputs (n:m)
   * see also [[DfsTransformer.transform()]]
   * @param options Options specified in the configuration for this transformation, including evaluated runtimeOptions
   */
  def transformWithOptions(actionId: ActionId, partitionValues: Seq[PartitionValues], dfs: Map[String,DataFrame], options: Map[String,String])(implicit context: ActionPipelineContext): Map[String,DataFrame]

  /**
   * Optional function to define the transformation of input to output partition values.
   * For example this enables to implement aggregations where multiple input partitions are combined into one output partition.
   * Note that the default value is input = output partition values, which should be correct for most use cases.
   * see also [[DfsTransformer.transformPartitionValues()]]
   * @param options Options specified in the configuration for this transformation, including evaluated runtimeOptions
   */
  def transformPartitionValuesWithOptions(actionId: ActionId, partitionValues: Seq[PartitionValues], options: Map[String,String])(implicit context: ActionPipelineContext): Option[Map[PartitionValues,PartitionValues]] = None

  override def transformPartitionValues(actionId: ActionId, partitionValues: Seq[PartitionValues])(implicit context: ActionPipelineContext): Option[Map[PartitionValues,PartitionValues]] = {
    // replace runtime options
    val runtimeOptionsReplaced = prepareRuntimeOptions(actionId, partitionValues)
    // transform
    transformPartitionValuesWithOptions(actionId, partitionValues, options ++ runtimeOptionsReplaced)
  }
  override def transform(actionId: ActionId, partitionValues: Seq[PartitionValues], dfs: Map[String,DataFrame])(implicit context: ActionPipelineContext): Map[String,DataFrame] = {
    // replace runtime options
    val runtimeOptionsReplaced = prepareRuntimeOptions(actionId, partitionValues)
    // transform
    transformWithOptions(actionId, partitionValues, dfs, options ++ runtimeOptionsReplaced )
  }
  private def prepareRuntimeOptions(actionId: ActionId, partitionValues: Seq[PartitionValues])(implicit context: ActionPipelineContext): Map[String,String] = {
    lazy val data = DefaultExpressionData.from(context, partitionValues)
    runtimeOptions.mapValues {
      expr => SparkExpressionUtil.evaluateString(actionId, Some(s"transformations.$name.runtimeOptions"), expr, data)
    }.filter(_._2.isDefined).mapValues(_.get)
  }